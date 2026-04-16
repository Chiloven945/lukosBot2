package top.chiloven.lukosbot2.core.auth.impl;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.AbstractTelegramClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import top.chiloven.lukosbot2.config.AppProperties;
import top.chiloven.lukosbot2.core.auth.IChatAdminResolver;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.platform.ChatPlatform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TelegramChatAdminResolver implements IChatAdminResolver {

    private static final long CACHE_TTL_MS = 60_000L;

    private final String token;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private volatile AbstractTelegramClient client;

    public TelegramChatAdminResolver(AppProperties props) {
        this.token = props.getTelegram() == null ? "" : props.getTelegram().getBotToken();
    }

    @Override
    public boolean supports(ChatPlatform platform) {
        return platform == ChatPlatform.TELEGRAM;
    }

    @Override
    public boolean isChatAdmin(CommandSource src) {
        if (!src.isGroup()) return false;
        Long userId = src.userIdOrNull();
        if (userId == null || token == null || token.isBlank()) return false;

        String key = src.chatId() + ":" + userId;
        CacheEntry hit = cache.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && hit.expiresAt > now) {
            return hit.value;
        }

        boolean value = queryIsAdmin(src.chatId(), userId);
        cache.put(key, new CacheEntry(value, now + CACHE_TTL_MS));
        return value;
    }

    private boolean queryIsAdmin(long chatId, long userId) {
        try {
            GetChatMember req = new GetChatMember(String.valueOf(chatId), userId);
            ChatMember member = telegramClient().execute(req);
            if (member == null || member.getStatus() == null) return false;
            String status = member.getStatus();
            return "creator".equalsIgnoreCase(status) || "administrator".equalsIgnoreCase(status);
        } catch (Exception _) {
            return false;
        }
    }

    private AbstractTelegramClient telegramClient() {
        AbstractTelegramClient c = client;
        if (c != null) return c;
        synchronized (this) {
            if (client == null) {
                client = new OkHttpTelegramClient(token);
            }
            return client;
        }
    }

    private record CacheEntry(boolean value, long expiresAt) {

    }

}
