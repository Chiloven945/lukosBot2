package top.chiloven.lukosbot2.platform.onebot;

import com.mikuac.shiro.core.BotContainer;
import top.chiloven.lukosbot2.model.MessageIn;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.platform.IReceiver;

import java.util.function.Consumer;

public final class OneBotReceiver implements IReceiver, AutoCloseable {

    private final BotContainer botContainer;

    public OneBotReceiver(BotContainer botContainer) {
        this.botContainer = botContainer;
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.ONEBOT;
    }

    /**
     * No-op: inbound messages are handled by {@link ShiroBridge} which directly calls
     * {@code MessageDispatcher.receive}.
     */
    @Override
    public void bind(Consumer<MessageIn> sink) {
        // no-op
    }

    @Override
    public void start() {
        // OneBot/Shiro connection is handled by Shiro's auto configuration.
    }

    @Override
    public void stop() {
        // nothing to stop here
    }

    @Override
    public void close() {
        // nothing to close
    }

    public OneBotSender sender() {
        return new OneBotSender(botContainer);
    }
}
