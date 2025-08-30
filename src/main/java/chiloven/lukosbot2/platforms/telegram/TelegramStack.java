package chiloven.lukosbot2.platforms.telegram;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

final class TelegramStack implements AutoCloseable {
    private final String token, username;
    TelegramBot bot;
    private TelegramBotsApi api;

    TelegramStack(String token, String username) {
        this.token = token;
        this.username = username;
    }

    void ensureStarted() throws Exception {
        if (bot != null) return;
        api = new TelegramBotsApi(DefaultBotSession.class);
        bot = new TelegramBot(token, username);
        api.registerBot(bot);
    }

    @Override
    public void close() { /* Telegram SDK 无显式 close，可省略 */ }
}
