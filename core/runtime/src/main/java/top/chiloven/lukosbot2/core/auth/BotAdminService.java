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
package top.chiloven.lukosbot2.core.auth;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import top.chiloven.lukosbot2.config.AppProperties;
import top.chiloven.lukosbot2.core.state.Scope;
import top.chiloven.lukosbot2.core.state.store.IStateStore;
import top.chiloven.lukosbot2.platform.ChatPlatform;

import java.util.*;

import static top.chiloven.lukosbot2.util.JsonUtils.MAPPER;

@Service
public class BotAdminService {

    private static final String NS_AUTH = "auth";
    private static final String KEY_BOT_ADMINS = "bot_admins";

    private final IStateStore store;
    private final AppProperties props;

    public BotAdminService(IStateStore store, AppProperties props) {
        this.store = store;
        this.props = props;
    }

    public boolean isBotAdmin(ChatPlatform platform, Long userId) {
        if (platform == null || userId == null) return false;
        return listEffectiveAdmins().getOrDefault(platform, Set.of()).contains(userId);
    }

    public Map<ChatPlatform, Set<Long>> listEffectiveAdmins() {
        Map<ChatPlatform, Set<Long>> out = new EnumMap<>(ChatPlatform.class);
        for (ChatPlatform platform : ChatPlatform.values()) {
            out.put(platform, new LinkedHashSet<>());
        }

        mergeInto(out, bootstrapAdmins());
        mergeInto(out, dynamicAdmins());

        Map<ChatPlatform, Set<Long>> readonly = new EnumMap<>(ChatPlatform.class);
        out.forEach((k, v) -> readonly.put(k, Collections.unmodifiableSet(v)));
        return Collections.unmodifiableMap(readonly);
    }

    private static void mergeInto(Map<ChatPlatform, Set<Long>> target, Map<ChatPlatform, Set<Long>> source) {
        source.forEach((platform, ids) -> target.computeIfAbsent(platform, _ -> new LinkedHashSet<>()).addAll(ids));
    }

    private Map<ChatPlatform, Set<Long>> bootstrapAdmins() {
        Map<ChatPlatform, Set<Long>> out = new EnumMap<>(ChatPlatform.class);
        AppProperties.Security security = props.getSecurity();
        if (security == null || security.getBootstrapBotAdmins() == null) {
            return out;
        }

        security.getBootstrapBotAdmins().forEach((platformRaw, ids) -> {
            if (platformRaw == null || ids == null) return;
            try {
                ChatPlatform platform = ChatPlatform.fromString(platformRaw);
                out.computeIfAbsent(platform, _ -> new LinkedHashSet<>()).addAll(ids);
            } catch (IllegalArgumentException _) {
            }
        });
        return out;
    }

    private Map<ChatPlatform, Set<Long>> dynamicAdmins() {
        return store.getJson(Scope.global(), NS_AUTH, KEY_BOT_ADMINS)
                .map(this::parseAdminsJson)
                .orElseGet(() -> new EnumMap<>(ChatPlatform.class));
    }

    private Map<ChatPlatform, Set<Long>> parseAdminsJson(String json) {
        Map<ChatPlatform, Set<Long>> out = new EnumMap<>(ChatPlatform.class);
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) return out;

            for (var entry : root.properties()) {
                String field = entry.getKey();
                ChatPlatform platform;
                try {
                    platform = ChatPlatform.fromString(field);
                } catch (IllegalArgumentException _) {
                    continue;
                }

                JsonNode arr = entry.getValue();
                if (arr == null || !arr.isArray()) continue;

                Set<Long> ids = out.computeIfAbsent(platform, _ -> new LinkedHashSet<>());
                for (JsonNode node : arr) {
                    if (node == null || !node.canConvertToLong()) continue;
                    ids.add(node.longValue());
                }
            }
        } catch (Exception _) {
        }
        return out;
    }

    public void addDynamicAdmin(ChatPlatform platform, long userId) {
        Map<ChatPlatform, Set<Long>> admins = dynamicAdmins();
        admins.computeIfAbsent(platform, _ -> new LinkedHashSet<>()).add(userId);
        saveDynamicAdmins(admins);
    }

    private void saveDynamicAdmins(Map<ChatPlatform, Set<Long>> admins) {
        Map<String, List<Long>> raw = new LinkedHashMap<>();
        for (ChatPlatform platform : ChatPlatform.values()) {
            Set<Long> ids = admins.getOrDefault(platform, Set.of());
            raw.put(platform.name(), ids.stream().sorted().toList());
        }
        store.upsertJson(
                Scope.global(),
                NS_AUTH,
                KEY_BOT_ADMINS,
                MAPPER.writeValueAsString(raw),
                null
        );
    }

    public void removeDynamicAdmin(ChatPlatform platform, long userId) {
        Map<ChatPlatform, Set<Long>> admins = dynamicAdmins();
        Set<Long> ids = admins.get(platform);
        if (ids != null) {
            ids.remove(userId);
        }
        saveDynamicAdmins(admins);
    }

    public Set<Long> bootstrapAdminsOf(ChatPlatform platform) {
        return Collections.unmodifiableSet(bootstrapAdmins().getOrDefault(platform, Set.of()));
    }

}
