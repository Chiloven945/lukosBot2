/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.platform.telegram;

import org.telegram.telegrambots.client.AbstractTelegramClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import top.chiloven.lukosbot2.util.HttpStatusException;

import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Collections;

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

    synchronized void ensureStarted() throws TelegramApiException {
        if (bot != null && app != null) return;

        TelegramBotsLongPollingApplication newApp = new TelegramBotsLongPollingApplication();
        TelegramBot newBot = new TelegramBot(username);
        try {
            newApp.registerBot(token, newBot);
            app = newApp;
            bot = newBot;
        } catch (TelegramApiException e) {
            try {
                newApp.stop();
            } catch (Exception _) {
            }
            throw e;
        }
    }

    @Override
    public synchronized void close() throws TelegramApiException {
        TelegramBotsLongPollingApplication oldApp = app;
        app = null;
        bot = null;

        if (oldApp != null) {
            oldApp.stop();
        }
    }

    <T extends Serializable, M extends BotApiMethod<T>> T execute(M method) {
        try {
            return client.execute(method);
        } catch (TelegramApiException e) {
            throw wrapTelegramApiException(e);
        }
    }

    private static RuntimeException wrapTelegramApiException(TelegramApiException e) {
        Integer statusCode = reflectInt(e, "getErrorCode", "getStatusCode", "getStatus");
        if (statusCode == null) {
            return new RuntimeException(e);
        }

        String snippet = reflectString(e, "getApiResponse", "getResponse", "getMessage");
        return new UncheckedIOException(new HttpStatusException(
                statusCode,
                "POST",
                "Telegram Bot API",
                snippet,
                null,
                Collections.emptyMap(),
                "Telegram Bot API HTTP " + statusCode + (snippet == null || snippet.isBlank() ? "" : ": " + snippet),
                e
        ));
    }

    private static Integer reflectInt(TelegramApiException e, String... methodNames) {
        for (String name : methodNames) {
            try {
                var method = e.getClass().getMethod(name);
                Object value = method.invoke(e);
                if (value instanceof Number number) {
                    return number.intValue();
                }
                if (value instanceof String text && !text.isBlank()) {
                    return Integer.parseInt(text.trim());
                }
            } catch (Exception _) {
                // Try the next known TelegramApiException accessor.
            }
        }
        return null;
    }

    private static String reflectString(TelegramApiException e, String... methodNames) {
        for (String name : methodNames) {
            try {
                var method = e.getClass().getMethod(name);
                Object value = method.invoke(e);
                if (value != null) {
                    String text = value.toString();
                    if (!text.isBlank()) return text;
                }
            } catch (Exception _) {
                // Try the next known TelegramApiException accessor.
            }
        }
        return e.getMessage();
    }

    Message execute(SendPhoto method) {
        try {
            return client.execute(method);
        } catch (TelegramApiException e) {
            throw wrapTelegramApiException(e);
        }
    }

    Message execute(SendDocument method) {
        try {
            return client.execute(method);
        } catch (TelegramApiException e) {
            throw wrapTelegramApiException(e);
        }
    }

}
