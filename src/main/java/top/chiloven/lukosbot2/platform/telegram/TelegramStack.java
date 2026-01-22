package top.chiloven.lukosbot2.platform.telegram;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

final class TelegramStack implements AutoCloseable {
    final String token, username;
    TelegramBot bot;
    private TelegramBotsLongPollingApplication app;

    TelegramStack(String token, String username) {
        this.token = token;
        this.username = username;
    }

    void ensureStarted() throws TelegramApiException {
        if (bot != null) return;
        app = new TelegramBotsLongPollingApplication();
        bot = new TelegramBot(username);
        app.registerBot(token, bot);
    }

    @Override
    public void close() throws TelegramApiException {
        if (app != null) app.stop();
    }
}
