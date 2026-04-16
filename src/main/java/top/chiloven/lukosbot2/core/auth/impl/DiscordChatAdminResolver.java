package top.chiloven.lukosbot2.core.auth.impl;

import org.springframework.stereotype.Component;
import top.chiloven.lukosbot2.core.auth.ChatAdminResolver;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.platform.ChatPlatform;

@Component
public class DiscordChatAdminResolver implements ChatAdminResolver {

    @Override
    public boolean supports(ChatPlatform platform) {
        return platform == ChatPlatform.DISCORD;
    }

    @Override
    public boolean isChatAdmin(CommandSource src) {
        Object explicit = src.ext("discord.chatAdmin");
        if (explicit instanceof Boolean b) return b;

        Object guildAdmin = src.ext("discord.guildAdmin");
        return guildAdmin instanceof Boolean b && b;
    }

}
