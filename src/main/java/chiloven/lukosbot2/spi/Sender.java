package chiloven.lukosbot2.spi;

import chiloven.lukosbot2.model.MessageOut;

/**
 * Sender interface: responsible for sending messages to the appropriate platform
 */
public interface Sender {
    /**
     * Send a message
     *
     * @param out message to send
     */
    void send(MessageOut out);
}
