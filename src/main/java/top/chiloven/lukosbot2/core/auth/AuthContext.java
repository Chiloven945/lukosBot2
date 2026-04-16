package top.chiloven.lukosbot2.core.auth;

/**
 * Immutable authorization snapshot for one actor in one execution context.
 *
 * <p>An {@code AuthContext} answers two related questions:</p>
 * <ul>
 *   <li>is the actor a global bot administrator?</li>
 *   <li>is the actor an administrator of the current chat context?</li>
 * </ul>
 *
 * <p>Global bot administrators are stronger than chat administrators and may manage both global
 * configuration and chat-local configuration. Chat administrators may only manage the current chat.</p>
 *
 * @param botAdmin  whether the actor is a bot-wide administrator.
 * @param chatAdmin whether the actor is an administrator of the current chat/guild/group/channel.
 */
public record AuthContext(
        boolean botAdmin,
        boolean chatAdmin
) {

    /**
     * Returns whether the actor may manage chat-scoped settings in the current context.
     *
     * @return {@code true} for bot admins and chat admins.
     */
    public boolean canManageChat() {
        return botAdmin || chatAdmin;
    }

    /**
     * Returns whether the actor may manage global bot settings.
     *
     * @return {@code true} only for bot admins.
     */
    public boolean canManageGlobal() {
        return botAdmin;
    }

}
