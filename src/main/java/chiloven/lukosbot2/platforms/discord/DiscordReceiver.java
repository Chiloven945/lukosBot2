package chiloven.lukosbot2.platforms.discord;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.platforms.ChatPlatform;
import chiloven.lukosbot2.spi.Receiver;
import chiloven.lukosbot2.spi.Sender;

import java.util.function.Consumer;

public final class DiscordReceiver implements Receiver {
    private final DiscordStack stack;
    private Consumer<MessageIn> sink = __ -> {
    };

    public DiscordReceiver(String token) {
        this.stack = new DiscordStack(token);
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.DISCORD;
    }

    /**
     * Bind message handler
     *
     * @param sink message handler, usually bound to Router::receive
     */
    @Override
    public void bind(Consumer<MessageIn> sink) {
        this.sink = (sink != null) ? sink : __ -> {
        };
        stack.setSink(this.sink);
    }

    /**
     * Start the receiver
     *
     * @throws Exception on failure
     */
    @Override
    public void start() throws Exception {
        stack.ensureStarted();
        stack.setSink(sink); // 确保 listener 引用的是最终 sink
    }

    @Override
    public void stop() {
        stack.close();
    }

    /**
     * Reuse the same connection to return a Sender (avoid Main touching Stack)
     *
     * @return a Sender using the same Discord bot instance
     * @throws Exception on failure
     */
    public Sender sender() throws Exception {
        start();
        return new DiscordSender(stack);
    }
}
