package top.chiloven.lukosbot2.core.auth;

import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.platform.ChatPlatform;

public interface ChatAdminResolver {

    boolean supports(ChatPlatform platform);

    boolean isChatAdmin(CommandSource src);

}
