package chiloven.lukosbot2.platform.telegram;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.platform.ChatPlatform;
import chiloven.lukosbot2.platform.Receiver;
import chiloven.lukosbot2.platform.Sender;

import java.util.function.Consumer;

public final class TelegramReceiver implements Receiver {
    private final TelegramStack stack;
    private Consumer<MessageIn> sink = __ -> {
    };

    public TelegramReceiver(String token, String username) {
        this.stack = new TelegramStack(token, username);
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.TELEGRAM;
    }

    @Override
    public void bind(Consumer<MessageIn> sink) {
        this.sink = (sink != null) ? sink : __ -> {
        };
        if (stack.bot != null) stack.bot.setSink(this.sink);
    }

    @Override
    public void start() throws Exception {
        stack.ensureStarted();
        stack.bot.setSink(sink);
    }

    @Override
    public void stop() { /* Telegram SDK 无显式 stop，可忽略 */ }

    public Sender sender() throws Exception {
        start();                           // 确保已启动（幂等）
        return new TelegramSender(stack);  // TelegramSender 复用同一个 stack.bot 发送
    }
}
