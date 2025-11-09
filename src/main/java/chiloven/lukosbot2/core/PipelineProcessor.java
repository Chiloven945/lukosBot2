package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * A configurable processing pipeline that applies a chain of {@link chiloven.lukosbot2.core.Processor} instances
 * to an inbound {@link chiloven.lukosbot2.model.MessageIn} and produces zero or more
 * {@link chiloven.lukosbot2.model.MessageOut} results; by default it short-circuits on the first processor that
 * yields output, and it can be switched to collect-from-all mode with optional parallel evaluation using virtual
 * threads.
 */
public class PipelineProcessor implements Processor {
    private static final Logger log = LogManager.getLogger(PipelineProcessor.class);
    private final List<Processor> chain = new ArrayList<>();
    private final Mode mode;
    private final boolean parallel;
    private final ExecutorService parallelExecutor;

    /**
     * Creates a pipeline configured with {@link Mode#STOP_ON_FIRST} and no parallelism, suitable for common
     * command-style flows where the first matching processor should produce the response and later processors
     * should be skipped.
     */
    public PipelineProcessor() {
        this(Mode.STOP_ON_FIRST, false);
    }

    /**
     * Creates a pipeline with the specified mode, disabling parallel execution; use this when you want either
     * short-circuiting or full collection while keeping processors evaluated sequentially.
     *
     * @param mode the pipeline mode determining whether to short-circuit on first output or collect outputs from
     *             all processors
     */
    public PipelineProcessor(Mode mode) {
        this(mode, false);
    }

    /**
     * Creates a pipeline with the specified mode and an optional parallel execution strategy; when
     * {@code parallel} is {@code true} and the mode is {@link Mode#COLLECT_ALL}, processors are evaluated
     * concurrently on virtual threads and their outputs are merged.
     *
     * @param mode     the pipeline mode ({@link Mode#STOP_ON_FIRST} to short-circuit, or {@link Mode#COLLECT_ALL}
     *                 to aggregate results)
     * @param parallel whether to evaluate processors concurrently (effective primarily with {@link Mode#COLLECT_ALL})
     */
    public PipelineProcessor(Mode mode, boolean parallel) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.parallel = parallel;
        this.parallelExecutor = parallel ? Execs.newVirtualExecutor() : null;
    }

    /**
     * Appends a processor to the end of the pipeline chain, preserving registration order for sequential
     * evaluation and determining short-circuit priority under {@link Mode#STOP_ON_FIRST}.
     *
     * @param p the processor to append; must not be {@code null}
     * @return this pipeline instance to allow fluent chaining
     */
    public PipelineProcessor add(Processor p) {
        chain.add(Objects.requireNonNull(p));
        return this;
    }

    /**
     * Processes the given input by running the registered processors according to the configured mode: in
     * {@link Mode#STOP_ON_FIRST} it returns the first non-empty result and skips the rest, while in
     * {@link Mode#COLLECT_ALL} it aggregates all non-empty results (optionally in parallel); processor
     * exceptions are logged and treated as producing no output.
     *
     * @param in the inbound message to process; must not be {@code null}
     * @return a list of produced messages, or an empty list if no processor yields output
     */
    @Override
    public List<MessageOut> handle(MessageIn in) {
        if (chain.isEmpty()) return Collections.emptyList();

        if (mode == Mode.STOP_ON_FIRST) {
            for (Processor p : chain) {
                List<MessageOut> r = safeHandle(p, in);
                if (!Processor.isEmpty(r)) return r; // 短路
            }
            return Collections.emptyList();
        }

        // COLLECT_ALL
        if (!parallel) {
            List<MessageOut> outs = new ArrayList<>();
            for (Processor p : chain) {
                List<MessageOut> r = safeHandle(p, in);
                if (!Processor.isEmpty(r)) outs.addAll(r);
            }
            return outs;
        } else {
            List<CompletableFuture<List<MessageOut>>> fs = chain.stream()
                    .map(p -> CompletableFuture.supplyAsync(() -> safeHandle(p, in), parallelExecutor))
                    .toList();
            List<MessageOut> outs = new ArrayList<>();
            for (CompletableFuture<List<MessageOut>> f : fs) {
                try {
                    List<MessageOut> r = f.get();
                    if (!Processor.isEmpty(r)) outs.addAll(r);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Pipeline interrupted", ie);
                } catch (ExecutionException ee) {
                    log.error("Processor execution failed", ee.getCause());
                }
            }
            return outs;
        }
    }

    /**
     * Invokes a single processor with error isolation, converting {@code null} or empty results to a canonical
     * empty list and logging any thrown exceptions; intended as a safe building block for the pipeline’s
     * evaluation strategy.
     *
     * @param p  the processor to execute; must not be {@code null}
     * @param in the inbound message to pass to the processor; must not be {@code null}
     * @return the processor’s non-empty output, or an empty list if it produced no results or failed
     */
    private List<MessageOut> safeHandle(Processor p, MessageIn in) {
        try {
            List<MessageOut> r = p.handle(in);
            return Processor.isEmpty(r) ? Processor.NO_OUTPUT : r;
        } catch (Throwable ex) {
            log.error("Processor {} error", p.getClass().getName(), ex);
            return Processor.NO_OUTPUT;
        }
    }

    /**
     * Execution mode of the pipeline: {@link #STOP_ON_FIRST} returns as soon as the first processor yields output,
     * whereas {@link #COLLECT_ALL} gathers outputs from every processor (with optional parallel evaluation) and
     * returns their concatenation.
     */
    public enum Mode {STOP_ON_FIRST, COLLECT_ALL}
}
