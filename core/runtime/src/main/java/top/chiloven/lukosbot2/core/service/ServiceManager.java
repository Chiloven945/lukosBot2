/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.core.service;

import top.chiloven.lukosbot2.config.ServiceConfigProp;
import top.chiloven.lukosbot2.core.BotCoroutineRuntime;
import top.chiloven.lukosbot2.core.ICancellableTask;
import top.chiloven.lukosbot2.core.MessageSenderHub;
import top.chiloven.lukosbot2.core.command.bot.CommandSource;
import top.chiloven.lukosbot2.core.model.ServiceConfig;
import top.chiloven.lukosbot2.core.model.ServiceEvent;
import top.chiloven.lukosbot2.core.model.message.Address;
import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage;
import top.chiloven.lukosbot2.core.state.Scope;
import top.chiloven.lukosbot2.core.state.ScopeType;
import top.chiloven.lukosbot2.core.state.store.IStateStore;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.services.IBotService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import static top.chiloven.lukosbot2.util.JsonUtils.MAPPER;

@Log4j2
public class ServiceManager {

    private static final String NS_SERVICE = "service";

    @Getter private final ServiceRegistry registry;
    private final IStateStore store;
    private final MessageSenderHub senderHub;
    private final ServiceConfigProp props;

    private final ConcurrentMap<String, ConcurrentMap<String, ServiceState>> chatStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ICancellableTask> schedules = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ServiceState> defaultStates = new ConcurrentHashMap<>();

    private final BotCoroutineRuntime runtime;

    public ServiceManager(
            ServiceRegistry registry,
            IStateStore store,
            MessageSenderHub senderHub,
            ServiceConfigProp props,
            BotCoroutineRuntime runtime
    ) {
        this.registry = registry;
        this.store = store;
        this.senderHub = senderHub;
        this.props = props;
        this.runtime = runtime;
    }

    public void init() {
        loadFromStore();

        var changed = ensureDefaultsEverywhere();
        rescheduleAllChats();

        if (changed) {
            // Ensure DB has all defaults + missing chat entries.
            persistAll();
        }
    }

    private void loadFromStore() {
        // defaults
        defaultStates.clear();
        store.getNamespaceJson(Scope.global(), NS_SERVICE)
                .forEach((serviceName, json) -> {
                    try {
                        var st = MAPPER.readValue(json, ServiceState.class);
                        if (st != null) {
                            defaultStates.put(serviceName, st);
                        }
                    } catch (Exception _) {
                    }
                });

        // chats
        chatStates.clear();
        store.scanByScopeTypeAndNamespace(ScopeType.CHAT, NS_SERVICE)
                .forEach((chatKey, kv) -> {
                    ConcurrentMap<String, ServiceState> cm = new ConcurrentHashMap<>();
                    if (kv != null) {
                        kv.forEach((serviceName, json) -> {
                            try {
                                var st = MAPPER.readValue(json, ServiceState.class);
                                if (st != null) {
                                    cm.put(serviceName, st);
                                }
                            } catch (Exception _) {
                            }
                        });
                    }
                    chatStates.put(chatKey, cm);
                });
    }

    private void persistAll() {
        defaultStates.forEach(this::persistDefault);
        chatStates.forEach(this::persistChatAll);
    }

    /**
     * Ensure:
     * <ul>
     *   <li>{@code defaultStates} has entries for every allowed service</li>
     *   <li>every known chat has entries for every allowed service</li>
     * </ul>
     *
     * @return {@code true} if any in-memory state was created and should be persisted.
     */
    private boolean ensureDefaultsEverywhere() {
        var changed = false;

        // defaults
        for (var s : registry.all()) {
            if (!props.isAllowed(s.name())) {
                continue;
            }

            if (!defaultStates.containsKey(s.name())) {
                defaultStates.put(
                        s.name(),
                        new ServiceState(
                                false,
                                new LinkedHashMap<>(s.defaultConfig())
                        )
                );
                changed = true;
            }
        }

        // each chat
        for (var ce : chatStates.entrySet()) {
            var chatKey = ce.getKey();
            var perChat = ce.getValue();
            var c = ensureDefaultsForChat(perChat);
            if (c) {
                persistChatAll(chatKey, perChat);
                changed = true;
            }
        }

        return changed;
    }

    private void rescheduleAllChats() {
        chatStates.keySet()
                .forEach(chatKey -> {
                    var addr = parseChatKey(chatKey);
                    if (addr == null) {
                        return;
                    }

                    var perChat = chatStates.get(chatKey);
                    registry.all().stream()
                            .filter(s -> props.isAllowed(s.name()))
                            .filter(s -> s.type() == ServiceType.TIME_BASED)
                            .forEach(s -> {
                                var st = perChat.get(s.name());
                                refreshSchedule(chatKey, addr, s, st);
                            });
                });
    }

    /**
     * Called by {@code MessageDispatcher} for incoming messages.
     *
     * @param in inbound message.
     *
     * @return outbound messages produced by enabled trigger services for this message; may be
     * empty.
     */
    public List<OutboundMessage> onMessage(InboundMessage in) {
        if (in == null || in.addr() == null) {
            return List.of();
        }

        var chatKey = chatKey(in.addr());
        var perChat = chatStates.computeIfAbsent(
                chatKey,
                _ -> new ConcurrentHashMap<>()
        );

        var changed = ensureDefaultsForChat(perChat);
        if (changed) {
            persistChatAll(chatKey, perChat);
        }

        List<OutboundMessage> outs = new ArrayList<>();
        var ctx = CommandSource.forInbound(in, outs::add);
        var ev = ServiceEvent.message(in);

        registry.all().stream()
                .filter(s -> props.isAllowed(s.name()))
                .forEach(s -> {
                    var st = perChat.get(s.name());
                    if (st == null || !st.isEnabled()) {
                        return;
                    }

                    if (s.type() == ServiceType.TRIGGER) {
                        try {
                            s.onEvent(ctx, new ServiceConfig(st.getConfig()), ev);
                        } catch (Exception e) {
                            log.warn(
                                    "Service {} failed on message: {}",
                                    s.name(),
                                    e.getMessage(),
                                    e
                            );
                        }
                    }
                });

        return outs;
    }

    private static String chatKey(Address addr) {
        return Scope.chatKey(addr);
    }

    private boolean ensureDefaultsForChat(ConcurrentMap<String, ServiceState> perChat) {
        var changed = false;

        for (var s : registry.all()) {
            if (!props.isAllowed(s.name())) {
                continue;
            }

            if (!perChat.containsKey(s.name())) {
                var d = defaultStates.get(s.name());
                if (d != null) {
                    perChat.put(
                            s.name(),
                            new ServiceState(d.isEnabled(), new LinkedHashMap<>(d.getConfig()))
                    );
                } else {
                    perChat.put(
                            s.name(),
                            new ServiceState(false, new LinkedHashMap<>(s.defaultConfig()))
                    );
                }
                changed = true;
            }
        }

        return changed;
    }

    private void persistChatAll(
            String chatKey,
            Map<String, ServiceState> perChat
    ) {
        if (perChat == null) {
            return;
        }

        perChat.forEach((key, value) ->
                persistChatState(chatKey, key, value)
        );
    }

    private void persistChatState(
            String chatKey,
            String serviceName,
            ServiceState st
    ) {
        store.upsertJson(
                new Scope(ScopeType.CHAT, chatKey),
                NS_SERVICE,
                serviceName,
                MAPPER.writeValueAsString(st),
                null
        );
    }

    /**
     * External trigger that fires one service event to one chat.
     *
     * @param addr        target chat.
     * @param serviceName service to invoke.
     * @param event       event payload.
     */
    public void fire(
            Address addr,
            String serviceName,
            ServiceEvent event
    ) {
        if (addr == null) {
            return;
        }

        var chatKey = chatKey(addr);
        var perChat = chatStates.computeIfAbsent(
                chatKey,
                _ -> new ConcurrentHashMap<>()
        );

        var changed = ensureDefaultsForChat(perChat);
        if (changed) {
            persistChatAll(chatKey, perChat);
        }

        var opt = registry.find(serviceName);
        if (opt.isEmpty()) {
            return;
        }

        var s = opt.get();
        if (!props.isAllowed(s.name())) {
            return;
        }
        if (s.type() != ServiceType.TRIGGER) {
            return;
        }

        var st = perChat.get(serviceName);
        if (st == null || !st.isEnabled()) {
            return;
        }

        CommandSource ctx;
        if (event != null && event.message() != null) {
            ctx = CommandSource.forInbound(event.message(), senderHub::send);
        } else {
            ctx = CommandSource.forAddress(addr, senderHub::send);
        }

        s.onEvent(ctx, new ServiceConfig(st.getConfig()), event);
    }

    /**
     * External trigger that fires one service event to all chats that currently have the service
     * enabled.
     *
     * @param serviceName service to invoke.
     * @param event       event payload.
     */
    public void fireAll(String serviceName, ServiceEvent event) {
        var opt = registry.find(serviceName);
        if (opt.isEmpty()) {
            return;
        }

        var s = opt.get();
        if (!props.isAllowed(s.name())) {
            return;
        }
        if (s.type() != ServiceType.TRIGGER) {
            return;
        }

        chatStates.forEach((chatKey, value) -> {
            var addr = parseChatKey(chatKey);
            if (addr == null) {
                return;
            }

            var st = value.get(serviceName);
            if (st == null || !st.isEnabled()) {
                return;
            }

            var ctx = CommandSource.forAddress(addr, senderHub::send);
            s.onEvent(ctx, new ServiceConfig(st.getConfig()), event);
        });
    }

    private static Address parseChatKey(String key) {
        try {
            var p = key.split(":", 3);
            var platform = ChatPlatform.valueOf(p[0]);
            var group = "g".equalsIgnoreCase(p[1]);
            var chatId = Long.parseLong(p[2]);
            return new Address(platform, chatId, group);
        } catch (Exception _) {
            return null;
        }
    }

    public boolean isAllowed(String serviceName) {
        return props.isAllowed(serviceName);
    }

    public Map<String, ServiceState> snapshotStates(Address addr) {
        var perChat = getOrCreateChatStates(addr);
        return new LinkedHashMap<>(perChat);
    }

    private ConcurrentMap<String, ServiceState> getOrCreateChatStates(Address addr) {
        var key = chatKey(addr);
        var perChat = chatStates.computeIfAbsent(
                key,
                _ -> new ConcurrentHashMap<>()
        );

        var changed = ensureDefaultsForChat(perChat);
        if (changed) {
            persistChatAll(key, perChat);
        }

        return perChat;
    }

    public Map<String, ServiceState> snapshotDefaultStates() {
        ensureDefaultStatesInitialized();
        return new LinkedHashMap<>(defaultStates);
    }

    private void ensureDefaultStatesInitialized() {
        var changed = false;
        for (var s : registry.all()) {
            if (!props.isAllowed(s.name())) {
                continue;
            }

            if (!defaultStates.containsKey(s.name())) {
                defaultStates.put(
                        s.name(),
                        new ServiceState(
                                false,
                                new LinkedHashMap<>(s.defaultConfig())
                        )
                );
                changed = true;
            }
        }

        if (changed) {
            defaultStates.forEach(this::persistDefault);
        }
    }

    private void persistDefault(String serviceName, ServiceState st) {
        store.upsertJson(
                Scope.global(),
                NS_SERVICE,
                serviceName,
                MAPPER.writeValueAsString(st),
                null
        );
    }

    public ServiceState stateOf(Address addr, String serviceName) {
        return getOrCreateChatStates(addr).get(serviceName);
    }

    public ServiceState defaultStateOf(String serviceName) {
        ensureDefaultStatesInitialized();
        return defaultStates.get(serviceName);
    }

    public void setEnabled(Address addr, String serviceName, boolean enabled) {
        if (!props.isAllowed(serviceName)) {
            return;
        }

        var chatKey = chatKey(addr);
        var perChat = getOrCreateChatStates(addr);

        var st = perChat.get(serviceName);
        if (st == null) {
            var d = defaultStates.get(serviceName);
            if (d != null) {
                st = new ServiceState(d.isEnabled(), new LinkedHashMap<>(d.getConfig()));
            } else {
                var svc = registry.find(serviceName).orElse(null);
                if (svc == null) {
                    return;
                }
                st = new ServiceState(false, new LinkedHashMap<>(svc.defaultConfig()));
            }
            perChat.put(serviceName, st);
        }

        st.setEnabled(enabled);

        var svc = registry.find(serviceName).orElse(null);
        if (svc != null && svc.type() == ServiceType.TIME_BASED) {
            refreshSchedule(chatKey, addr, svc, st);
        }

        persistChatState(chatKey, serviceName, st);
    }

    private void refreshSchedule(String chatKey, Address addr, IBotService s, ServiceState st) {
        var sk = scheduleKey(chatKey, s.name());

        var old = schedules.remove(sk);
        if (old != null) {
            old.cancel();
        }

        if (st == null || !st.isEnabled()) {
            return;
        }

        var intervalMs = new ServiceConfig(st.getConfig()).intervalMs(60_000L);

        var task = (Runnable) () -> {
            try {
                var ctx = CommandSource.forAddress(addr, senderHub::send);
                s.onTick(ctx, new ServiceConfig(st.getConfig()));
            } catch (Exception e) {
                log.warn("Service tick failed: {}", s.name(), e);
            }
        };

        var f = runtime.scheduleAtFixedRate(
                "service-" + sk,
                intervalMs,
                intervalMs,
                task
        );
        schedules.put(sk, f);
    }

    private static String scheduleKey(String chatKey, String serviceName) {
        return chatKey + "|" + serviceName;
    }

    public void setDefaultEnabled(String serviceName, boolean enabled) {
        if (!props.isAllowed(serviceName)) {
            return;
        }
        ensureDefaultStatesInitialized();

        var st = defaultStates.get(serviceName);
        if (st == null) {
            var svc = registry.find(serviceName).orElse(null);
            if (svc == null) {
                return;
            }

            st = new ServiceState(
                    false,
                    new LinkedHashMap<>(svc.defaultConfig())
            );
            defaultStates.put(serviceName, st);
        }

        st.setEnabled(enabled);
        persistDefault(serviceName, st);
    }

    public void setConfigValue(
            Address addr,
            String serviceName,
            String key,
            String value
    ) {
        if (!props.isAllowed(serviceName)) {
            return;
        }

        var chatKey = chatKey(addr);
        var perChat = getOrCreateChatStates(addr);

        var st = perChat.get(serviceName);
        if (st == null) {
            var d = defaultStates.get(serviceName);
            if (d != null) {
                st = new ServiceState(d.isEnabled(), new LinkedHashMap<>(d.getConfig()));
            } else {
                var svc = registry.find(serviceName).orElse(null);
                if (svc == null) {
                    return;
                }

                st = new ServiceState(false, new LinkedHashMap<>(svc.defaultConfig()));
            }

            perChat.put(serviceName, st);
        }

        if (st.getConfig() == null) {
            st.setConfig(new LinkedHashMap<>());
        }

        if (value == null) {
            st.getConfig().remove(key);
        } else {
            st.getConfig().put(key, value);
        }

        var svc = registry.find(serviceName).orElse(null);
        if (svc != null && svc.type() == ServiceType.TIME_BASED) {
            refreshSchedule(chatKey, addr, svc, st);
        }

        persistChatState(chatKey, serviceName, st);
    }

    public void setDefaultConfigValue(
            String serviceName,
            String key,
            String value
    ) {
        if (!props.isAllowed(serviceName)) {
            return;
        }
        ensureDefaultStatesInitialized();

        var st = defaultStates.get(serviceName);
        if (st == null) {
            var svc = registry.find(serviceName).orElse(null);
            if (svc == null) {
                return;
            }

            st = new ServiceState(false, new LinkedHashMap<>(svc.defaultConfig()));
            defaultStates.put(serviceName, st);
        }

        if (st.getConfig() == null) {
            st.setConfig(new LinkedHashMap<>());
        }

        if (value == null) {
            st.getConfig().remove(key);
        } else {
            st.getConfig().put(key, value);
        }

        persistDefault(serviceName, st);
    }

    public void destroy() {
        schedules.values().stream()
                .filter(Objects::nonNull)
                .forEach(ICancellableTask::cancel);
        schedules.clear();
    }

}
