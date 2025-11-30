// TelegramBot.java
package chiloven.lukosbot2.platform.telegram;

import chiloven.lukosbot2.model.Address;
import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.platform.ChatPlatform;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.function.Consumer;

final class TelegramBot implements LongPollingSingleThreadUpdateConsumer {
    private final String username;

    private Consumer<MessageIn> sink = __ -> {
    };

    public TelegramBot(String username) {
        this.username = username;
    }

    /**
     * Set message handler
     *
     * @param sink message handler, usually bound to MessageDispatcher::receive
     */
    void setSink(Consumer<MessageIn> sink) {
        this.sink = (sink != null) ? sink : __ -> {
        };
    }

    @Override
    public void consume(Update u) {
        if (u == null || u.getMessage() == null || u.getMessage().getText() == null) return;

        long chatId = u.getMessage().getChatId();
        boolean isGroup = u.getMessage().getChat().isGroupChat() || u.getMessage().getChat().isSuperGroupChat();
        Long userId = (u.getMessage().getFrom() != null) ? u.getMessage().getFrom().getId() : null;
        String text = u.getMessage().getText();
        sink.accept(new MessageIn(new Address(ChatPlatform.TELEGRAM, chatId, isGroup), userId, text));
    }
}
