package chiloven.lukosbot2.platform.onebot;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.platform.ChatPlatform;
import chiloven.lukosbot2.platform.Receiver;

import java.util.function.Consumer;

public final class OneBotReceiver implements Receiver, AutoCloseable {

    private Consumer<MessageIn> sink = __ -> {
    };

    // 兼容旧构造签名（wsUrl / token 将由 Shiro 的 application.yml 处理，这里不再使用）
    private final String url;
    private final String token;

    public OneBotReceiver(String url, String token) {
        this.url = url;
        this.token = token;
    }

    @Override
    public ChatPlatform platform() {
        return ChatPlatform.ONEBOT;
    }


    @Override
    public void bind(Consumer<MessageIn> sink) {
        this.sink = (sink != null) ? sink : __ -> {
        };
        ShiroBridge.setSink(this.sink);
    }

    @Override
    public void start() {
        // 交由 Shiro 根据 application.yml 自动建立连接与事件监听，这里无需操作
    }

    public OneBotSender sender() {
        return new OneBotSender();
    }

    @Override
    public void close() {
        // 解绑回调；Shiro 的 WS 生命周期由其自身管理
        ShiroBridge.setSink(__ -> {
        });
    }

    @Override
    public void stop() {
    }
}
