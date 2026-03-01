package top.chiloven.lukosbot2.platform;

import top.chiloven.lukosbot2.model.message.inbound.InboundMessage;

import java.util.function.Consumer;

/**
 * Receiver interface: responsible for receiving messages from a specific platform and passing them to the message
 * handler.
 */
public interface IReceiver extends AutoCloseable {

    ChatPlatform platform();

    /**
     * Bind message handler.
     *
     * @param sink message handler, usually bound to MessageDispatcher::receive
     */
    void bind(Consumer<InboundMessage> sink);

    /**
     * Start the receiver.
     *
     * @throws Exception throw exception if start failed
     */
    void start() throws Exception;

    /**
     * Close the receiver, equivalent to stop().
     *
     * @throws Exception throw exception if stop failed
     */
    @Override
    default void close() throws Exception {
        stop();
    }

    /**
     * Stop the receiver.
     *
     * @throws Exception throw exception if stop failed
     */
    void stop() throws Exception;

}
