package chiloven.lukosbot2.spi;

import chiloven.lukosbot2.model.ChatPlatform;
import chiloven.lukosbot2.model.MessageIn;

import java.util.function.Consumer;

/**
 * 接收器接口：负责从指定平台接收消息
 */
public interface Receiver extends AutoCloseable {
    ChatPlatform platform();

    /**
     * 绑定消息处理函数
     *
     * @param sink 消息处理函数
     */
    void bind(Consumer<MessageIn> sink);  // 通常绑定到 Router::receive

    /**
     * 启动接收器
     *
     * @throws Exception 启动失败时抛出异常
     */
    void start() throws Exception;

    /**
     * 停止接收器
     *
     * @throws Exception 停止失败时抛出异常
     */
    void stop() throws Exception;

    /**
     * 关闭接收器，默认调用 stop 方法
     *
     * @throws Exception 关闭失败时抛出异常
     */
    @Override
    default void close() throws Exception {
        stop();
    }
}
