package chiloven.lukosbot2.platforms;

import chiloven.lukosbot2.model.MessageIn;

import java.util.function.Consumer;

/**
 * Receiver interface: responsible for receiving messages from a specific platform and passing them to the message handler
 */
public interface Receiver extends AutoCloseable {
    ChatPlatform platform();

    /**
     * Bind message handler
     *
     * @param sink message handler, usually bound to MessageDispatcher::receive
     */
    void bind(Consumer<MessageIn> sink);

    /**
     * Start the receiver
     *
     * @throws Exception throw exception if start failed
     */
    void start() throws Exception;

    /**
     * Stop the receiver
     *
     * @throws Exception throw exception if stop failed
     */
    void stop() throws Exception;

    /**
     * Close the receiver, equivalent to stop()
     *
     * @throws Exception throw exception if stop failed
     */
    @Override
    default void close() throws Exception {
        stop();
    }
}
