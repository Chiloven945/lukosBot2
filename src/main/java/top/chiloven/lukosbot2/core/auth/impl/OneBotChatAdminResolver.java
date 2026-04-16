package top.chiloven.lukosbot2.core.auth.impl;

import org.springframework.stereotype.Component;
import top.chiloven.lukosbot2.core.auth.IChatAdminResolver;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.platform.ChatPlatform;

@Component
public class OneBotChatAdminResolver implements IChatAdminResolver {

    @Override
    public boolean supports(ChatPlatform platform) {
        return platform == ChatPlatform.ONEBOT;
    }

    @Override
    public boolean isChatAdmin(CommandSource src) {
        Object role = src.ext("onebot.groupRole");
        if (!(role instanceof String s)) return false;
        return "owner".equalsIgnoreCase(s) || "admin".equalsIgnoreCase(s);
    }

}
