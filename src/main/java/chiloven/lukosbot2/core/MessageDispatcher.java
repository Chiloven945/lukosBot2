package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 新 Dispatcher：
 * - 可选 prefix 快速过滤（不进入流水线）
 * - StripedExecutor：跨 chat 并行、同 chat 有序
 * - 下游发送可批量 & 保序
 */
public class MessageDispatcher {
    private static final Logger log = LogManager.getLogger(MessageDispatcher.class);

    private final Processor pipeline;
    private final MessageSenderHub msh;
    private final StripedExecutor lanes;
    private final String prefix;            // 可选命令前缀（如 "!"）
    private final boolean preserveOrder;    // 批量发送时是否保序

    /**
     * 兼容旧构造：默认无前缀过滤、并发条带=2*CPU、发送保序
     */
    public MessageDispatcher(Processor pipeline, MessageSenderHub msh) {
        this(pipeline, msh, null, Math.max(2, Runtime.getRuntime().availableProcessors() * 2), true);
    }

    public MessageDispatcher(Processor pipeline,
                             MessageSenderHub msh,
                             String prefix,
                             int stripes,
                             boolean preserveOrder) {
        this.pipeline = Objects.requireNonNull(pipeline);
        this.msh = Objects.requireNonNull(msh);
        this.prefix = prefix;
        this.preserveOrder = preserveOrder;
        this.lanes = new StripedExecutor(stripes, "lane-%d");
    }

    /**
     * 入口：接收消息 ->（可选）前缀快速过滤 -> 进入条带执行 -> 流水线 -> 批量发送
     */
    public void receive(MessageIn in) {
        String oneLine = StringUtils.oneLine(in.text());
        log.info("IN <- [{}] user={} chat={} text=\"{}\"",
                in.addr().platform(), in.userId(), in.addr().chatId(), oneLine);

        // 快速过滤：不满足前缀，直接丢弃（不进入 pipeline）
        if (prefix != null) {
            String t = (in.text() == null ? "" : in.text().trim());
            if (!t.startsWith(prefix)) {
                return;
            }
        }

        Object key = in.addr().chatId(); // 保证同 chat 顺序
        lanes.submit(key, () -> {
            long t0 = System.nanoTime();
            List<MessageOut> outs = pipeline.handle(in);
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

            if (outs == null || outs.isEmpty()) {
                log.info("PIPELINE result: empty ({} ms)", costMs);
                return;
            }
            log.info("PIPELINE result: {} message(s) ({} ms)", outs.size(), costMs);

            // 批量发送（可配置保序）
            msh.sendBatch(outs, preserveOrder);
        });
    }
}
