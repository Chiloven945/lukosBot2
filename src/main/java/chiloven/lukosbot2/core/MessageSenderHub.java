package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.platforms.ChatPlatform;
import chiloven.lukosbot2.platforms.Sender;
import chiloven.lukosbot2.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 路由中心：
 * - 按平台路由到不同 Sender
 * - 提供 send/sendAsync/sendBatch
 * - 批量发送时可选择是否保序
 *
 * 说明：Java 25 环境，统一使用虚拟线程执行发送任务。
 */
public class MessageSenderHub {
    private static final Logger log = LogManager.getLogger(MessageSenderHub.class);
    private final Map<ChatPlatform, Sender> routes = new EnumMap<>(ChatPlatform.class);

    private final ExecutorService sendPool = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 注册平台 sender
     */
    public void register(ChatPlatform p, Sender s) {
        routes.put(Objects.requireNonNull(p), Objects.requireNonNull(s));
    }

    /** 兼容旧方法：同步（当前线程） */
    public void send(MessageOut out) {
        int att = (out.attachments() == null) ? 0 : out.attachments().size();
        log.info("OUT -> [{}] to chat={} text=\"{}\" attachments={}",
                out.addr().platform(), out.addr().chatId(), StringUtils.oneLine(out.text()), att);
        Sender s = routes.get(out.addr().platform());
        if (s == null) {
            log.warn("No Sender for platform: {}", out.addr().platform());
            return;
        }
        try {
            s.send(out);
        } catch (Throwable ex) {
            log.error("Send failed on platform {}", out.addr().platform(), ex);
        }
    }

    /**
     * 异步发送（虚拟线程）
     */
    public CompletableFuture<Void> sendAsync(MessageOut out) {
        return CompletableFuture.runAsync(() -> send(out), sendPool);
    }

    /**
     * 批量发送；preserveOrder=true 时按顺序逐条发送，否则并发发送
     */
    public void sendBatch(List<MessageOut> outs, boolean preserveOrder) {
        if (outs == null || outs.isEmpty()) return;
        if (preserveOrder) {
            for (MessageOut out : outs) send(out);
        } else {
            CompletableFuture<?>[] fs = outs.stream()
                    .map(this::sendAsync)
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(fs).join();
        }
    }
}
