package chiloven.lukosbot2.platforms.telegram;

import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.spi.Sender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TelegramSender implements Sender {
    private static final Logger log = LogManager.getLogger(TelegramSender.class);

    private final TelegramStack stack;

    TelegramSender(TelegramStack stack) {
        this.stack = stack;
    }

    @Override
    public void send(MessageOut out) {
        try {
            stack.bot.send(out);
            log.info("Telegram message sent: {}", out);
        } catch (Exception ignored) {
        }
    }
}
