package top.chiloven.lukosbot2.core.state;

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
    CHAT
}
