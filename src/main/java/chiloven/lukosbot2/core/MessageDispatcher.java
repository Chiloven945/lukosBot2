package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * High-throughput dispatcher that optionally filters messages by prefix, executes the processing
 * pipeline on a virtual-thread pool for parallelism, and hands off results to striped single-thread
 * lanes to guarantee per-chat ordering while allowing cross-chat concurrency; when configured to
 * serialize per chat, messages from the same chat are sent in order, otherwise batches may be sent
 * concurrently without ordering guarantees.
 */
public class MessageDispatcher implements AutoCloseable {
    public static final StringUtils SU = new StringUtils();
    private static final Logger log = LogManager.getLogger(MessageDispatcher.class);
    private final Processor pipeline;
    private final MessageSenderHub msh;
    private final StripedExecutor lanes;
    private final ExecutorService procPool;
    private final String prefix;
    private final boolean serializePerChat;

    /**
     * Constructs a dispatcher with sensible defaults—no prefix filtering, a stripe count of
     * {@code max(2, CPU*2)} for per-chat serialization, and ordered batch sending—suitable for most
     * deployments without additional tuning.
     *
     * @param pipeline the processing pipeline that transforms a {@link chiloven.lukosbot2.model.MessageIn}
     *                 into zero or more {@link chiloven.lukosbot2.model.MessageOut} instances
     * @param msh      the hub responsible for routing and transmitting produced messages to platform senders
     */
    public MessageDispatcher(Processor pipeline, MessageSenderHub msh) {
        this(pipeline, msh, null, Math.max(2, Runtime.getRuntime().availableProcessors() * 2), true);
    }

    /**
     * Constructs a dispatcher with explicit prefix filtering, stripe count, and per-chat serialization
     * behavior; messages not starting with the given prefix (when non-{@code null}) are dropped early,
     * processing runs on a virtual-thread pool, and results are delivered on striped lanes either in
     * order per chat or concurrently depending on {@code serializePerChat}.
     *
     * @param pipeline         the processing pipeline that handles inbound messages
     * @param msh              the hub that performs the actual sending of produced messages
     * @param prefix           the required message prefix for fast filtering; {@code null} disables the check
     * @param stripes          the number of striped single-thread lanes used for delivery
     * @param serializePerChat whether to preserve per-chat send order ({@code true}) or allow
     *                         concurrent, out-of-order sending within a chat ({@code false})
     */
    public MessageDispatcher(Processor pipeline, MessageSenderHub msh, String prefix, int stripes, boolean serializePerChat) {
        this.pipeline = Objects.requireNonNull(pipeline);
        this.msh = Objects.requireNonNull(msh);
        this.prefix = prefix;
        this.serializePerChat = serializePerChat;
        this.lanes = new StripedExecutor(Math.max(1, stripes), "lane-%02d");
        this.procPool = Execs.newVirtualExecutor("proc-%02d");
    }

    /**
     * Receives an inbound message, applies the optional prefix filter, executes the pipeline on the
     * virtual-thread pool to produce zero or more outputs, and enqueues delivery on striped lanes with
     * ordering determined by {@code serializePerChat}.
     *
     * @param in the incoming message to process
     */
    public void receive(MessageIn in) {
        log.info("IN <- [{}] user={} chat={} text=\"{}\"",
                in.addr().platform(), in.userId(), in.addr().chatId(), SU.truncate(in.text()));

        if (prefix != null) {
            String t = (in.text() == null ? "" : in.text().trim());
            if (!t.startsWith(prefix)) return;
        }

        procPool.submit(() -> {
            long t0 = System.nanoTime();
            List<MessageOut> outs = pipeline.handle(in);
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

            if (outs == null || outs.isEmpty()) {
                log.info("PIPELINE result: empty ({} ms)", costMs);
                return;
            }
            log.info("PIPELINE result: {} message(s) ({} ms)", outs.size(), costMs);

            if (serializePerChat) {
                Object key = in.addr().chatId(); // 同一 chat 保序发送
                lanes.submit(key, () -> msh.sendBatch(outs, true));
            } else {
                Object key = ThreadLocalRandom.current().nextLong();
                lanes.submit(key, () -> msh.sendBatch(outs, false));
            }
        });
    }

    /**
     * Closes the dispatcher by initiating an orderly shutdown of the striped lane executors and the
     * virtual-thread processing pool so no new tasks are accepted and in-flight tasks can complete.
     */
    @Override
    public void close() {
        try {
            lanes.close();
        } finally {
            procPool.shutdown();
        }
    }
}
