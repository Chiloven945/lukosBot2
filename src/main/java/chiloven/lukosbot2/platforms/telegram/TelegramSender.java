// TelegramSender.java
package chiloven.lukosbot2.platforms.telegram;

import chiloven.lukosbot2.model.Attachment;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.spi.Sender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;

public final class TelegramSender implements Sender {
    private static final Logger log = LogManager.getLogger(TelegramSender.class);
    private final TelegramClient client;

    TelegramSender(TelegramStack stack) {
        this.client = new OkHttpTelegramClient(stack.token);
    }

    private static String safeName(String name, String fallback) {
        return (name == null || name.isBlank()) ? fallback : name;
    }

    @Override
    public void send(MessageOut out) {
        long chatId = out.addr().chatId();

        // 文本
        if (out.text() != null && !out.text().isBlank()) {
            try {
                client.execute(SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text(out.text())
                        .build());
                log.info("Telegram send to chatId={}", chatId);
            } catch (Exception e) {
                log.error("Telegram text failed", e);
            }
        }

        // 附件
        if (out.attachments() == null) return;
        for (Attachment a : out.attachments()) {
            log.debug("Processing attachment: type={}, name={}", a.type(), a.name());
            try {
                switch (a.type()) {
                    case IMAGE -> {
                        InputFile image = (a.bytes() != null)
                                ? new InputFile(new ByteArrayInputStream(a.bytes()), safeName(a.name(), "image.bin"))
                                : new InputFile(a.url());
                        client.execute(SendPhoto.builder()
                                .chatId(String.valueOf(chatId))
                                .photo(image)
                                .build());
                        log.info("Telegram photo sent.");
                    }
                    case FILE -> {
                        InputFile file = (a.bytes() != null)
                                ? new InputFile(new ByteArrayInputStream(a.bytes()), safeName(a.name(), "file.bin"))
                                : new InputFile(a.url());
                        client.execute(SendDocument.builder()
                                .chatId(String.valueOf(chatId))
                                .document(file)
                                .build());
                        log.info("Telegram document sent.");
                    }
                    default -> log.debug("Unknown attachment type: {}.", a.type());
                }
            } catch (Exception e) {
                log.error("Telegram attachment failed: type={}, name={}.", a.type(), a.name(), e);
            }
        }
    }
}
