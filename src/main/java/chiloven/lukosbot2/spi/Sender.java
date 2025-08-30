package chiloven.lukosbot2.spi;

import chiloven.lukosbot2.model.MessageOut;

/**
 * 发送器接口：负责将消息发送到指定平台
 */
public interface Sender {
    /**
     * 发送消息
     *
     * @param out 要发送的消息
     */
    void send(MessageOut out);
}
