package chiloven.lukosbot2.platforms.telegram;

import chiloven.lukosbot2.model.Address;
import chiloven.lukosbot2.model.ChatPlatform;
import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.function.Consumer;

final class TelegramBot extends TelegramLongPollingBot {
    private final String username;
    private Consumer<MessageIn> sink = __ -> {
    };

    TelegramBot(String token, String username) {
        super(token);
        this.username = username;
    }

    /**
     * Bind message handler
     *
     * @param sink message handler, usually bound to Router::receive
     */
    void setSink(Consumer<MessageIn> sink) {
        this.sink = (sink != null) ? sink : __ -> {
        };
    }

    @Override
    public String getBotUsername() {
        return username;
    } // 仍需实现

    @Override
    public void onUpdateReceived(Update u) {
        if (u.getMessage() == null || u.getMessage().getText() == null) return;
        long chatId = u.getMessage().getChatId();
        boolean isGroup = u.getMessage().getChat().isGroupChat() || u.getMessage().getChat().isSuperGroupChat();
        Long userId = (u.getMessage().getFrom() != null) ? u.getMessage().getFrom().getId() : null;
        String text = u.getMessage().getText();
        sink.accept(new MessageIn(new Address(ChatPlatform.TELEGRAM, chatId, isGroup), userId, text));
    }

    void send(MessageOut out) throws Exception {
        execute(SendMessage.builder()
                .chatId(String.valueOf(out.addr().chatId()))
                .text(out.text())
                .build());
    }
}
