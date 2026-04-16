package top.chiloven.lukosbot2.core.auth;

import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.platform.ChatPlatform;

/**
 * Strategy interface for resolving whether the current actor should be treated as an administrator of the active chat,
 * guild, group, or channel on a given platform.
 *
 * <p>This interface only answers <em>chat-scoped</em> authority. Global bot administrators are handled
 * separately by {@link BotAdminService} and typically override chat-level checks in {@link AuthorizationService}.</p>
 *
 * <p>Implementations may inspect cached metadata stored in {@link CommandSource#ext()}, call a platform API,
 * or combine both approaches. A resolver should return {@code false} when admin status cannot be determined reliably;
 * callers must not assume administrative privileges on partial evidence.</p>
 */
public interface IChatAdminResolver {

    /**
     * Returns whether this resolver can evaluate chat admin status for the given platform.
     *
     * @param platform chat platform to check.
     * @return {@code true} if this resolver supports the platform; otherwise {@code false}.
     */
    boolean supports(ChatPlatform platform);

    /**
     * Determines whether the actor represented by {@code src} is an administrator of the current chat context.
     *
     * <p>Examples include a Telegram group administrator, a Discord member with the relevant guild/channel
     * management permissions, or a OneBot group owner/admin.</p>
     *
     * @param src unified command/service source.
     * @return {@code true} if the actor is a chat administrator; otherwise {@code false}.
     */
    boolean isChatAdmin(CommandSource src);

}
