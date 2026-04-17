package top.chiloven.lukosbot2.core.policy;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.config.AppProperties;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.platform.ChatPlatform;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Log4j2
public class PolicyService {

    private static final String EXT_PRIVATE_CHAT = "policy.privateChat";
    private static final String EXT_NSFW = "policy.nsfw";

    private final AppProperties props;

    public PolicyService(AppProperties props) {
        this.props = props;
    }

    public boolean isCommandAllowed(CommandSource src, String commandName) {
        String normalized = normalize(commandName);
        if (normalized == null) return true;
        return !resolveDisabledCommands(src).contains(normalized);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private Set<String> resolveDisabledCommands(CommandSource src) {
        LinkedHashSet<String> disabled = new LinkedHashSet<>();
        for (AppProperties.Policy.Rule rule : matchedRules(src)) {
            if (rule.getDisableCommands() == null) continue;
            disabled.addAll(normalizedSet(rule.getDisableCommands()));
        }
        return disabled;
    }

    private List<AppProperties.Policy.Rule> matchedRules(CommandSource src) {
        AppProperties.Policy policy = props == null ? null : props.getPolicy();
        if (policy == null || policy.getRules() == null || policy.getRules().isEmpty()) {
            return List.of();
        }

        PolicyContext ctx = PolicyContext.from(src);

        return policy.getRules().stream()
                .filter(Objects::nonNull)
                .filter(rule -> matches(rule.getWhen(), ctx))
                .sorted(Comparator
                        .comparingInt(AppProperties.Policy.Rule::getPriority)
                        .reversed())
                .toList();
    }

    private static LinkedHashSet<String> normalizedSet(Collection<String> values) {
        if (values == null || values.isEmpty()) return new LinkedHashSet<>();
        return values.stream()
                .map(PolicyService::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean matches(AppProperties.Policy.Match when, PolicyContext ctx) {
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
        if (expected == null) return true;
        if (actual == null) return false;
        return expected.equalsIgnoreCase(actual);
    }

    private static <T> boolean equalsNullable(T expected, T actual) {
        return expected == null || Objects.equals(expected, actual);
    }

    public Set<String> allowedValues(CommandSource src, String key, Collection<String> defaults) {
        String normalizedKey = normalizeKey(key);
        if (normalizedKey == null) {
            return normalizedSet(defaults);
        }

        LinkedHashSet<String> result = defaults == null ? null : normalizedSet(defaults);

        for (AppProperties.Policy.Rule rule : matchedRules(src)) {
            Map<String, List<String>> allowValues = rule.getAllowValues();
            if (allowValues == null || allowValues.isEmpty()) continue;

            List<String> allowed = findAllowValues(allowValues, normalizedKey);
            if (allowed == null) continue;

            LinkedHashSet<String> normalizedAllowed = normalizedSet(allowed);
            if (result == null) {
                result = normalizedAllowed;
            } else {
                result.retainAll(normalizedAllowed);
            }
        }

        return result == null ? Set.of() : Set.copyOf(result);
    }

    private static String normalizeKey(String key) {
        return normalize(key);
    }

    private static List<String> findAllowValues(Map<String, List<String>> allowValues, String normalizedKey) {
        for (Map.Entry<String, List<String>> entry : allowValues.entrySet()) {
            if (normalizeKey(entry.getKey()) != null && normalizeKey(entry.getKey()).equals(normalizedKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String commandDeniedMessage(String commandName) {
        String display = commandName == null || commandName.isBlank() ? "该命令" : commandName;
        return "当前上下文不允许使用命令：" + display;
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
            ChatPlatform platform = src == null ? null : src.platform();
            String platformName = platform == null ? null : platform.name().toLowerCase(Locale.ROOT);
            boolean group = src != null && src.isGroup();
            boolean privateChat = src != null && readBoolean(src, EXT_PRIVATE_CHAT, !group);
            boolean nsfw = src != null && readBoolean(src, EXT_NSFW, false);
            long chatId = src == null ? 0L : src.chatId();
            Long userId = src == null ? null : src.userIdOrNull();
            return new PolicyContext(platformName, group, privateChat, nsfw, chatId, userId);
        }

        private static boolean readBoolean(CommandSource src, String key, boolean defaultValue) {
            Object value = src.ext(key);
            if (value instanceof Boolean b) return b;
            if (value instanceof String s) {
                if ("true".equalsIgnoreCase(s)) return true;
                if ("false".equalsIgnoreCase(s)) return false;
            }
            return defaultValue;
        }

    }

}
