package top.chiloven.lukosbot2.platform;

import top.chiloven.lukosbot2.model.MessageOut;

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
