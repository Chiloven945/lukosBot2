package chiloven.lukosbot2.platforms.telegram;

import chiloven.lukosbot2.model.Attachment;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.spi.Sender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.ByteArrayInputStream;

public final class TelegramSender implements Sender {
    private static final Logger log = LogManager.getLogger(TelegramSender.class);

    private final TelegramStack stack;

    TelegramSender(TelegramStack stack) {
        this.stack = stack;
    }

    private static String safeName(String name, String fallback) {
        return (name == null || name.isBlank()) ? fallback : name;
    }

    /**
     * Send a message via Telegram bot API.
     *
     * @param out message to send
     */
    @Override
    public void send(MessageOut out) {
        long chatId = out.addr().chatId();

        // 1) Texts
        if (out.text() != null && !out.text().isBlank()) {
            try {
                stack.bot.execute(SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text(out.text())
                        .build());
                log.info("Telegram text sent: {}", out.text());
            } catch (Exception e) {
                log.warn("Telegram send text failed", e);
            }
        }

        // 2) Attachments
        if (out.attachments() == null) return;
        for (Attachment a : out.attachments()) {
            try {
                switch (a.type()) {
                    case IMAGE -> {
                        InputFile file = (a.bytes() != null)
                                ? new InputFile(new ByteArrayInputStream(a.bytes()), safeName(a.name(), "image.bin"))
                                : new InputFile(a.url());
                        stack.bot.execute(SendPhoto.builder()
                                .chatId(String.valueOf(chatId))
                                .photo(file)
                                .build());
                    }
                    case FILE -> {
                        InputFile file = (a.bytes() != null)
                                ? new InputFile(new ByteArrayInputStream(a.bytes()), safeName(a.name(), "file.bin"))
                                : new InputFile(a.url());
                        stack.bot.execute(SendDocument.builder()
                                .chatId(String.valueOf(chatId))
                                .document(file)
                                .build());
                    }
                    default -> {
                    }
                }
            } catch (Exception e) {
                log.warn("Telegram send attachment failed: {}", a, e);
            }
        }
    }
}
