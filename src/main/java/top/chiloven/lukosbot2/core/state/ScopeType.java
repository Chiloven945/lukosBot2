package top.chiloven.lukosbot2.core.state;

import java.util.EnumSet;

/**
 * Scope type for persisted states.
 */
public enum ScopeType {

    /**
     * Global scope (applies to all users/chats unless overridden).
     */
    GLOBAL,
    /**
     * User scope (per-platform user).
     */
    USER,
    /**
     * Chat scope (per-platform chat).
     */
    CHAT;

    public static EnumSet<ScopeType> all() {
        return EnumSet.allOf(ScopeType.class);
    }

}
