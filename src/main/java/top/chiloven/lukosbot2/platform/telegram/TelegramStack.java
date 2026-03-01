package top.chiloven.lukosbot2.platform.telegram;

import org.telegram.telegrambots.client.AbstractTelegramClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;

final class TelegramStack implements AutoCloseable {

    final String token, username;
    final AbstractTelegramClient client;

    TelegramBot bot;
    private TelegramBotsLongPollingApplication app;

    TelegramStack(String token, String username) {
        this.token = token;
        this.username = username;
        this.client = new OkHttpTelegramClient(token);
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

    <T extends Serializable, M extends BotApiMethod<T>> T execute(M method) {
        try {
            return client.execute(method);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    Message execute(SendPhoto method) {
        try {
            return client.execute(method);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    Message execute(SendDocument method) {
        try {
            return client.execute(method);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

}
