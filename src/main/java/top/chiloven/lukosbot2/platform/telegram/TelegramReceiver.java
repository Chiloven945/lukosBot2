package top.chiloven.lukosbot2.platform.telegram;

import top.chiloven.lukosbot2.model.message.inbound.InboundMessage;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.platform.IReceiver;
import top.chiloven.lukosbot2.platform.ISender;

import java.util.function.Consumer;

public final class TelegramReceiver implements IReceiver {

    private final TelegramStack stack;
    private Consumer<InboundMessage> sink = _ -> {
    };

    public TelegramReceiver(String token, String username) {
        this.stack = new TelegramStack(token, username);
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.TELEGRAM;
    }

    @Override
    public void bind(Consumer<InboundMessage> sink) {
        this.sink = (sink != null) ? sink : _ -> {
        };
        if (stack.bot != null) stack.bot.setSink(this.sink);
    }

    @Override
    public void start() throws Exception {
        stack.ensureStarted();
        stack.bot.setSink(sink);
    }

    @Override
    public void stop() {
        try {
            stack.close();
        } catch (Exception ignored) {
        }
    }

    public ISender sender() throws Exception {
        start();                           // 确保已启动（幂等）
        return new TelegramSender(stack);  // TelegramSender 复用同一个 stack.bot 发送
    }

}
