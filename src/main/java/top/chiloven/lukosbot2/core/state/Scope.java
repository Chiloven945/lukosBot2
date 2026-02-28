package top.chiloven.lukosbot2.core.state;

import org.jspecify.annotations.NonNull;
import top.chiloven.lukosbot2.model.Address;
import top.chiloven.lukosbot2.platform.ChatPlatform;

/**
 * A scope identifies where a piece of state is stored.
 *
 * <p>We store different kinds of states (prefs, services, etc.) in a single table, partitioned by:
 * (scopeType, scopeId, namespace, key) -> json</p>
 */
public record Scope(ScopeType type, String id) {

    public static @NonNull Scope global() {
        return new Scope(ScopeType.GLOBAL, "_");
    }

    public static @NonNull Scope user(@NonNull ChatPlatform platform, long userId) {
        return new Scope(ScopeType.USER, platform.name() + ":" + userId);
    }

    public static @NonNull Scope chat(@NonNull Address addr) {
        return new Scope(ScopeType.CHAT, chatKey(addr));
    }

    /**
     * Chat key format: PLATFORM:(g|p):chatId
     */
    public static @NonNull String chatKey(@NonNull Address addr) {
        return addr.platform().name() + ":" + (addr.group() ? "g" : "p") + ":" + addr.chatId();
    }

}
