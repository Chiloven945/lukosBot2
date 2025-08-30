package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.ChatPlatform;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.spi.Sender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.Map;

public class SenderMux implements Sender {
    private static final Logger log = LogManager.getLogger(SenderMux.class);
    private final Map<ChatPlatform, Sender> routes = new EnumMap<>(ChatPlatform.class);

    public void register(ChatPlatform p, Sender s) {
        routes.put(p, s);
    }

    /**
     * 发送消息
     *
     * @param out 要发送的消息
     */
    @Override
    public void send(MessageOut out) {
        Sender s = routes.get(out.addr().platform());
        if (s == null) {
            log.warn("未找到 Sender：{}", out.addr().platform());
            return;
        }
        s.send(out);
    }
}
