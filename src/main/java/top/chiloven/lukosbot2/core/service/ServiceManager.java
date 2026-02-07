package top.chiloven.lukosbot2.core.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.config.ServiceConfigProp;
import top.chiloven.lukosbot2.core.MessageSenderHub;
import top.chiloven.lukosbot2.core.service.store.IServiceStateStore;
import top.chiloven.lukosbot2.model.*;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.services.IBotService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Service
@Log4j2
public class ServiceManager {

    private final ServiceRegistry registry;
    private final IServiceStateStore store;
    private final MessageSenderHub senderHub;
    private final ServiceConfigProp props;

    /**
     * chatKey -> (serviceName -> state)
     */
    private final ConcurrentMap<String, ConcurrentMap<String, ServiceState>> chatStates = new ConcurrentHashMap<>();

    /**
     * scheduleKey(chatKey|serviceName) -> future
     */
    private final ConcurrentMap<String, ScheduledFuture<?>> schedules = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "service-scheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * defaults for new chats
     */
    private final ConcurrentMap<String, ServiceState> defaultStates = new ConcurrentHashMap<>();

    public ServiceManager(ServiceRegistry registry,
                          IServiceStateStore store,
                          MessageSenderHub senderHub,
                          ServiceConfigProp props) {
        this.registry = registry;
        this.store = store;
        this.senderHub = senderHub;
        this.props = props;
    }

    @PostConstruct
    void init() {
        ServiceStateDoc doc = store.load();

        // load defaults
        defaultStates.clear();
        if (doc.defaults() != null) {
            defaultStates.putAll(doc.defaults());
        }

        // load chats
        chatStates.clear();
        if (doc.chats() != null) {
            doc.chats().forEach((chatKey, m) -> {
                ConcurrentMap<String, ServiceState> cm = new ConcurrentHashMap<>();
                if (m != null) cm.putAll(m);
                chatStates.put(chatKey, cm);
            });
        }

        boolean changed = ensureDefaultsEverywhere();
        rescheduleAllChats();

        if (changed) {
            persist();
        }
    }

    /**
     * Ensure: - defaultStates has entries for every allowed service - every chat has entries for every allowed service
     */
    private boolean ensureDefaultsEverywhere() {
        boolean changed = false;

        // defaults
        for (IBotService s : registry.all()) {
            if (!props.isAllowed(s.name())) continue;

            if (!defaultStates.containsKey(s.name())) {
                defaultStates.put(
                        s.name(),
                        new ServiceState(false, new LinkedHashMap<>(s.defaultConfig()))
                );
                changed = true;
            }
        }

        // each chat
        for (ConcurrentMap<String, ServiceState> perChat : chatStates.values()) {
            changed |= ensureDefaultsForChat(perChat);
        }

        return changed;
    }

    private void rescheduleAllChats() {
        for (String chatKey : chatStates.keySet()) {
            Address addr = parseChatKey(chatKey);
            if (addr == null) continue;

            ConcurrentMap<String, ServiceState> perChat = chatStates.get(chatKey);

            for (IBotService s : registry.all()) {
                if (!props.isAllowed(s.name())) continue;
                if (s.type() != ServiceType.TIME_BASED) continue;

                ServiceState st = perChat.get(s.name());
                refreshSchedule(chatKey, addr, s, st);
            }
        }
    }

    private void persist() {
        Map<String, Map<String, ServiceState>> chats = new LinkedHashMap<>();
        for (var ce : chatStates.entrySet()) {
            chats.put(ce.getKey(), new LinkedHashMap<>(ce.getValue()));
        }
        store.save(new ServiceStateDoc(new LinkedHashMap<>(defaultStates), chats));
    }

    private boolean ensureDefaultsForChat(ConcurrentMap<String, ServiceState> perChat) {
        boolean changed = false;

        for (IBotService s : registry.all()) {
            if (!props.isAllowed(s.name())) continue;

            if (!perChat.containsKey(s.name())) {
                ServiceState d = defaultStates.get(s.name());
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

    /* ======================== scheduling ======================== */

    private static Address parseChatKey(String key) {
        try {
            String[] p = key.split(":", 3);
            ChatPlatform platform = ChatPlatform.valueOf(p[0]);
            boolean group = "g".equalsIgnoreCase(p[1]);
            long chatId = Long.parseLong(p[2]);
            return new Address(platform, chatId, group);
        } catch (Exception e) {
            return null;
        }
    }

    private void refreshSchedule(String chatKey, Address addr, IBotService s, ServiceState st) {
        String sk = scheduleKey(chatKey, s.name());

        ScheduledFuture<?> old = schedules.remove(sk);
        if (old != null) old.cancel(false);

        if (st == null || !st.isEnabled()) return;

        // default 60s
        long intervalMs = new ServiceConfig(st.getConfig()).intervalMs(60_000L);

        Runnable task = () -> {
            try {
                ServiceContext ctx = new ServiceContext(senderHub, addr);
                s.onTick(ctx, new ServiceConfig(st.getConfig()));
            } catch (Exception e) {
                log.warn("Service tick failed: {}", s.name(), e);
            }
        };

        ScheduledFuture<?> f = scheduler.scheduleAtFixedRate(task, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        schedules.put(sk, f);
    }

    private static String scheduleKey(String chatKey, String serviceName) {
        return chatKey + "|" + serviceName;
    }

    /* ======================== incoming + triggers ======================== */

    /**
     * Called by MessageDispatcher (incoming messages).
     */
    public void onMessage(MessageIn in) {
        String chatKey = chatKey(in.addr());
        ConcurrentMap<String, ServiceState> perChat = chatStates.computeIfAbsent(chatKey, k -> new ConcurrentHashMap<>());

        boolean changed = ensureDefaultsForChat(perChat);
        if (changed) persist();

        ServiceContext ctx = new ServiceContext(senderHub, in.addr());
        ServiceEvent ev = ServiceEvent.message(in);

        for (IBotService s : registry.all()) {
            if (!props.isAllowed(s.name())) continue;

            ServiceState st = perChat.get(s.name());
            if (st == null || !st.isEnabled()) continue;

            if (s.type() == ServiceType.TRIGGER) {
                s.onEvent(ctx, new ServiceConfig(st.getConfig()), ev);
            }
        }
    }

    private static String chatKey(Address addr) {
        return addr.platform().name() + ":" + (addr.group() ? "g" : "p") + ":" + addr.chatId();
    }

    /**
     * External trigger: fire an event to a single chat.
     */
    public void fire(Address addr, String serviceName, ServiceEvent event) {
        String chatKey = chatKey(addr);
        ConcurrentMap<String, ServiceState> perChat = chatStates.computeIfAbsent(chatKey, k -> new ConcurrentHashMap<>());

        boolean changed = ensureDefaultsForChat(perChat);
        if (changed) persist();

        Optional<IBotService> opt = registry.find(serviceName);
        if (opt.isEmpty()) return;

        IBotService s = opt.get();
        if (!props.isAllowed(s.name())) return;
        if (s.type() != ServiceType.TRIGGER) return;

        ServiceState st = perChat.get(serviceName);
        if (st == null || !st.isEnabled()) return;

        ServiceContext ctx = new ServiceContext(senderHub, addr);
        s.onEvent(ctx, new ServiceConfig(st.getConfig()), event);
    }

    /**
     * External trigger: fire an event to ALL chats that enabled the given service.
     */
    public void fireAll(String serviceName, ServiceEvent event) {
        Optional<IBotService> opt = registry.find(serviceName);
        if (opt.isEmpty()) return;

        IBotService s = opt.get();
        if (!props.isAllowed(s.name())) return;
        if (s.type() != ServiceType.TRIGGER) return;

        for (var ce : chatStates.entrySet()) {
            String chatKey = ce.getKey();
            Address addr = parseChatKey(chatKey);
            if (addr == null) continue;

            ServiceState st = ce.getValue().get(serviceName);
            if (st == null || !st.isEnabled()) continue;

            ServiceContext ctx = new ServiceContext(senderHub, addr);
            s.onEvent(ctx, new ServiceConfig(st.getConfig()), event);
        }
    }

    /* ======================== public API for /service command ======================== */

    public ServiceRegistry registry() {
        return registry;
    }

    public boolean isAllowed(String serviceName) {
        return props.isAllowed(serviceName);
    }

    public Map<String, ServiceState> snapshotStates(Address addr) {
        ConcurrentMap<String, ServiceState> perChat = getOrCreateChatStates(addr);
        return new LinkedHashMap<>(perChat);
    }

    private ConcurrentMap<String, ServiceState> getOrCreateChatStates(Address addr) {
        String key = chatKey(addr);
        ConcurrentMap<String, ServiceState> perChat = chatStates.computeIfAbsent(key, k -> new ConcurrentHashMap<>());

        boolean changed = ensureDefaultsForChat(perChat);
        if (changed) persist();

        return perChat;
    }

    public ServiceState stateOf(Address addr, String serviceName) {
        return getOrCreateChatStates(addr).get(serviceName);
    }

    public void setEnabled(Address addr, String serviceName, boolean enabled) {
        if (!props.isAllowed(serviceName)) return;

        ConcurrentMap<String, ServiceState> perChat = getOrCreateChatStates(addr);

        ServiceState st = perChat.get(serviceName);
        if (st == null) {
            // prefer defaults, then service.defaultConfig
            ServiceState d = defaultStates.get(serviceName);
            if (d != null) {
                st = new ServiceState(d.isEnabled(), new LinkedHashMap<>(d.getConfig()));
            } else {
                IBotService svc = registry.find(serviceName).orElse(null);
                if (svc == null) return;
                st = new ServiceState(false, new LinkedHashMap<>(svc.defaultConfig()));
            }
            perChat.put(serviceName, st);
        }

        st.setEnabled(enabled);

        // update schedules for TIME_BASED
        IBotService svc = registry.find(serviceName).orElse(null);
        if (svc != null && svc.type() == ServiceType.TIME_BASED) {
            refreshSchedule(chatKey(addr), addr, svc, st);
        }

        persist();
    }

    public void setConfigValue(Address addr, String serviceName, String key, String value) {
        if (!props.isAllowed(serviceName)) return;

        ConcurrentMap<String, ServiceState> perChat = getOrCreateChatStates(addr);

        ServiceState st = perChat.get(serviceName);
        if (st == null) {
            // create with defaults
            ServiceState d = defaultStates.get(serviceName);
            if (d != null) {
                st = new ServiceState(d.isEnabled(), new LinkedHashMap<>(d.getConfig()));
            } else {
                IBotService svc = registry.find(serviceName).orElse(null);
                if (svc == null) return;
                st = new ServiceState(false, new LinkedHashMap<>(svc.defaultConfig()));
            }
            perChat.put(serviceName, st);
        }

        if (st.getConfig() == null) {
            st.setConfig(new LinkedHashMap<>());
        }

        if (value == null) st.getConfig().remove(key);
        else st.getConfig().put(key, value);

        // if TIME_BASED and interval changed, reschedule
        IBotService svc = registry.find(serviceName).orElse(null);
        if (svc != null && svc.type() == ServiceType.TIME_BASED) {
            refreshSchedule(chatKey(addr), addr, svc, st);
        }

        persist();
    }
}
