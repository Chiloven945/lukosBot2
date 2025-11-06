package chiloven.lukosbot2.platforms.telegram;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.platforms.ChatPlatform;
import chiloven.lukosbot2.platforms.Receiver;
import chiloven.lukosbot2.platforms.Sender;

import java.util.function.Consumer;

public final class TelegramReceiver implements Receiver {
    private final TelegramStack stack;                 // ← 内部持有
    private Consumer<MessageIn> sink = __ -> {
    };

    public TelegramReceiver(String token, String username) {
        this.stack = new TelegramStack(token, username);
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.TELEGRAM;
    }

    /**
     * Bind message handler
     *
     * @param sink message handler, usually bound to MessageDispatcher::receive
     */
    @Override
    public void bind(Consumer<MessageIn> sink) {
        this.sink = (sink != null) ? sink : __ -> {
        };
        if (stack.bot != null) stack.bot.setSink(this.sink);
    }

    /**
     * Start the receiver
     *
     * @throws Exception on failure
     */
    @Override
    public void start() throws Exception {
        stack.ensureStarted();
        stack.bot.setSink(sink);
    }

    @Override
    public void stop() { /* Telegram SDK 无显式 stop，可忽略 */ }

    /**
     * Reuse the same connection to return a Sender (avoid Main touching Stack)
     */
    public Sender sender() throws Exception {
        start();                           // 确保已启动（幂等）
        return new TelegramSender(stack);  // TelegramSender 已使用同一个 stack.bot 发送
    }
}
