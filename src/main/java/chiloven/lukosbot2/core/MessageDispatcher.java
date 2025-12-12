package chiloven.lukosbot2.core;

import chiloven.lukosbot2.config.AppProperties;
import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import chiloven.lukosbot2.util.StringUtils;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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
@Service
public class MessageDispatcher implements AutoCloseable {
    public static final StringUtils su = StringUtils.getStringUtils();
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
     * @param pipeline the processing pipeline that transforms a {@link MessageIn}
     *                 into zero or more {@link MessageOut} instances
     * @param msh      the hub responsible for routing and transmitting produced messages to platform senders
     */
    public MessageDispatcher(
            PipelineProcessor pipeline,
            @Qualifier("messageSenderHub") MessageSenderHub msh,
            AppProperties props
    ) {
        this.pipeline = Objects.requireNonNull(pipeline);
        this.msh = Objects.requireNonNull(msh);
        this.prefix = props.getPrefix();
        this.serializePerChat = true;

        int stripes = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
        this.lanes = new StripedExecutor(stripes, "lane-%02d");
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
                in.addr().platform(), in.userId(), in.addr().chatId(), su.truncate(in.text()));

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
                Object key = in.addr().chatId();
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
    @PreDestroy
    public void close() {
        try {
            lanes.close();
        } finally {
            procPool.shutdown();
        }
    }
}
