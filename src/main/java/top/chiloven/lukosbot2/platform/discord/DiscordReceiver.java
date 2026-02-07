package top.chiloven.lukosbot2.platform.discord;

import top.chiloven.lukosbot2.config.ProxyConfigProp;
import top.chiloven.lukosbot2.model.MessageIn;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.platform.IReceiver;
import top.chiloven.lukosbot2.platform.ISender;

import java.util.function.Consumer;

public final class DiscordReceiver implements IReceiver {

    private final DiscordStack stack;
    private Consumer<MessageIn> sink = __ -> {
    };

    public DiscordReceiver(String token, ProxyConfigProp proxyConfigProp) {
        this.stack = new DiscordStack(token, proxyConfigProp);
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.DISCORD;
    }

    @Override
    public void bind(Consumer<MessageIn> sink) {
        this.sink = (sink != null) ? sink : __ -> {
        };
        stack.setSink(this.sink);
    }

    public ISender sender() throws Exception {
        start(); // idempotent
        return new DiscordSender(stack);
    }

    @Override
    public void stop() {
        stack.close();
    }

    @Override
    public void start() throws Exception {
        stack.ensureStarted();
        stack.setSink(sink);
    }
}
