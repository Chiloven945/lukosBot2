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
 * Dispatches incoming messages by applying an optional prefix filter, scheduling processing on striped
 * single-thread lanes keyed by chat ID to guarantee per-chat ordering while allowing cross-chat concurrency,
 * and batching the resulting outputs for delivery via {@link MessageSenderHub} with optional order
 * preservation; intended as the high-throughput gateway from receivers to the processing pipeline.
 */
public class MessageDispatcher {
    private static final Logger log = LogManager.getLogger(MessageDispatcher.class);

    private final Processor pipeline;
    private final MessageSenderHub msh;
    private final StripedExecutor lanes;
    private final String prefix;
    private final boolean preserveOrder;

    /**
     * Creates a dispatcher with no prefix filtering, a default stripe count of {@code max(2, CPU*2)} for
     * per-chat serialization, and batch sending with order preserved; use this when you want sensible
     * defaults without additional tuning.
     *
     * @param pipeline the processing pipeline that transforms a {@link chiloven.lukosbot2.model.MessageIn}
     *                 into zero or more {@link chiloven.lukosbot2.model.MessageOut} messages
     * @param msh      the hub responsible for routing and sending outgoing messages to their
     *                 platform-specific{@link chiloven.lukosbot2.platforms.Sender}s
     */
    public MessageDispatcher(Processor pipeline, MessageSenderHub msh) {
        this(pipeline, msh, null, Math.max(2, Runtime.getRuntime().availableProcessors() * 2), true);
    }

    /**
     * Creates a dispatcher configured with explicit prefix filtering, stripe count, and batch-sending order
     * semantics; messages not starting with the given prefix (when non-null) are dropped early, messages
     * from the same chat ID are serialized on the same lane, and produced outputs are sent in a batch with
     * optional order preservation.
     *
     * @param pipeline      the processing pipeline that handles inbound messages
     * @param msh           the hub that performs actual sending of produced messages
     * @param prefix        the required message prefix for quick filtering; {@code null} disables prefix checks
     * @param stripes       the number of striped single-thread lanes used to serialize processing per chat while
     *                      enabling cross-chat parallelism
     * @param preserveOrder whether to preserve the order of messages within each batch during sending
     */
    public MessageDispatcher(Processor pipeline, MessageSenderHub msh, String prefix, int stripes, boolean preserveOrder) {
        this.pipeline = Objects.requireNonNull(pipeline);
        this.msh = Objects.requireNonNull(msh);
        this.prefix = prefix;
        this.preserveOrder = preserveOrder;
        this.lanes = new StripedExecutor(stripes, "lane-%d");
    }

    /**
     * Receives an inbound message, applies the optional prefix filter, enqueues accepted work onto the lane
     * determined by the message’s chat ID for ordered processing, executes the pipeline to produce zero or
     * more outputs, and forwards any results to the sender hub as a batch according to the configured order
     * policy.
     *
     * @param in the incoming message to process
     */
    public void receive(MessageIn in) {
        log.info("IN <- [{}] user={} chat={} text=\"{}\"",
                in.addr().platform(), in.userId(), in.addr().chatId(), StringUtils.oneLine(in.text()));

        if (prefix != null) {
            String t = (in.text() == null ? "" : in.text().trim());
            if (!t.startsWith(prefix)) return;
        }

        Object key = in.addr().chatId();
        lanes.submit(key, () -> {
            long t0 = System.nanoTime();
            List<MessageOut> outs = pipeline.handle(in);
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

            if (outs == null || outs.isEmpty()) {
                log.info("PIPELINE result: empty ({} ms)", costMs);
                return;
            }
            log.info("PIPELINE result: {} message(s) ({} ms)", outs.size(), costMs);
            msh.sendBatch(outs, preserveOrder);
        });
    }
}
