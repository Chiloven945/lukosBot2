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
package top.chiloven.lukosbot2.core.policy;

import top.chiloven.lukosbot2.config.AppProperties;
import top.chiloven.lukosbot2.core.command.bot.CommandSource;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PolicyService {

    private static final String EXT_PRIVATE_CHAT = "policy.privateChat";
    private static final String EXT_NSFW = "policy.nsfw";

    private final AppProperties props;

    public PolicyService(AppProperties props) {
        this.props = props;
    }

    public boolean isCommandAllowed(CommandSource src, String commandName) {
        var normalized = normalize(commandName);
        return normalized == null || !resolveDisabledCommands(src).contains(normalized);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private Set<String> resolveDisabledCommands(CommandSource src) {
        var disabled = new LinkedHashSet<String>();
        matchedRules(src).forEach(rule -> {
            rule.getDisableCommands();
            disabled.addAll(normalizedSet(rule.getDisableCommands()));
        });
        return disabled;
    }

    private List<AppProperties.Policy.Rule> matchedRules(CommandSource src) {
        var policy = props == null
                ? null
                : props.getPolicy();
        if (policy == null || policy.getRules().isEmpty()) {
            return List.of();
        }

        var ctx = PolicyContext.from(src);

        return policy.getRules().stream()
                .filter(rule -> matches(rule.getWhen(), ctx))
                .sorted(Comparator
                        .comparingInt(AppProperties.Policy.Rule::getPriority)
                        .reversed()
                )
                .toList();
    }

    private static LinkedHashSet<String> normalizedSet(Collection<String> values) {
        return values == null || values.isEmpty()
                ? new LinkedHashSet<>()
                : values.stream()
                        .map(PolicyService::normalize)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean matches(
            AppProperties.Policy.Match when,
            PolicyContext ctx
    ) {
        return when == null || (
                equalsIgnoreCaseNullable(when.getPlatform(), ctx.platformName) &&
                        equalsNullable(when.getPrivateChat(), ctx.privateChat) &&
                        equalsNullable(when.getGroup(), ctx.group) &&
                        equalsNullable(when.getNsfw(), ctx.nsfw) &&
                        equalsNullable(when.getChatId(), ctx.chatId) &&
                        equalsNullable(when.getUserId(), ctx.userId)
        );
    }

    private static boolean equalsIgnoreCaseNullable(String expected, String actual) {
        if (expected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return expected.equalsIgnoreCase(actual);
    }

    private static <T> boolean equalsNullable(T expected, T actual) {
        return expected == null || Objects.equals(expected, actual);
    }

    public Set<String> allowedValues(
            CommandSource src,
            String key,
            Collection<String> defaults
    ) {
        var normalizedKey = normalizeKey(key);
        if (normalizedKey == null) {
            return normalizedSet(defaults);
        }

        var result = defaults == null
                ? null
                : normalizedSet(defaults);

        for (var rule : matchedRules(src)) {
            var allowValues = rule.getAllowValues();
            if (allowValues.isEmpty()) {
                continue;
            }

            var allowed = findAllowValues(allowValues, normalizedKey);
            if (allowed == null) {
                continue;
            }

            var normalizedAllowed = normalizedSet(allowed);
            if (result == null) {
                result = normalizedAllowed;
            } else {
                result.retainAll(normalizedAllowed);
            }
        }

        return result == null
                ? Set.of()
                : Set.copyOf(result);
    }

    private static String normalizeKey(String key) {
        return normalize(key);
    }

    private static List<String> findAllowValues(
            Map<String, List<String>> allowValues,
            String normalizedKey
    ) {
        return allowValues.entrySet().stream()
                .filter(entry ->
                        normalizeKey(entry.getKey()) != null
                                && normalizeKey(entry.getKey()).equals(normalizedKey)
                )
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    public String commandDeniedMessage(String commandName) {
        var display = commandName == null || commandName.isBlank()
                ? "该命令"
                : commandName;
        return "当前聊天不允许使用此命令：" + display;
    }

    private record PolicyContext(
            String platformName,
            boolean group,
            boolean privateChat,
            boolean nsfw,
            long chatId,
            Long userId
    ) {

        static PolicyContext from(CommandSource src) {
            var platform = src == null
                    ? null
                    : src.platform();
            var platformName = platform == null
                    ? null
                    : platform.name().toLowerCase(Locale.ROOT);
            var group = src != null && src.isGroup();
            var privateChat = src != null && readBoolean(src, EXT_PRIVATE_CHAT, !group);
            var nsfw = src != null && readBoolean(src, EXT_NSFW, false);
            var chatId = src == null
                    ? 0L
                    : src.chatId();
            var userId = src == null
                    ? null
                    : src.userIdOrNull();
            return new PolicyContext(
                    platformName,
                    group,
                    privateChat,
                    nsfw,
                    chatId,
                    userId
            );
        }

        private static boolean readBoolean(
                CommandSource src,
                String key,
                boolean defaultValue
        ) {
            var value = src.ext(key);
            if (value instanceof Boolean b) {
                return b;
            }

            if (value instanceof String s) {
                if ("true".equalsIgnoreCase(s)) {
                    return true;
                }

                if ("false".equalsIgnoreCase(s)) {
                    return false;
                }
            }

            return defaultValue;
        }

    }

}
