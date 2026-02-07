package top.chiloven.lukosbot2.core;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.core.command.CommandProcessor;
import top.chiloven.lukosbot2.model.MessageIn;
import top.chiloven.lukosbot2.model.MessageOut;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Spring-managed pipeline processor that delegates to an internal {@link Pipeline}
 * composed of one or more {@link IProcessor} instances.
 * <p>
 * Default wiring: only {@link CommandProcessor} in STOP_ON_FIRST mode.
 */
@Service
@Log4j2
public class PipelineProcessor implements IProcessor {

    private final Pipeline delegate;

    /**
     * Spring constructor: build a pipeline with a single CommandProcessor, short-circuiting on first non-empty result.
     */
    public PipelineProcessor(CommandProcessor commandProcessor) {
        this.delegate = new Pipeline(List.of(commandProcessor), Mode.STOP_ON_FIRST, false);
    }

    @Override
    public List<MessageOut> handle(MessageIn in) {
        return delegate.handle(in);
    }

    /**
     * Execution mode of the pipeline.
     */
    public enum Mode {
        /**
         * Return as soon as first processor yields output.
         */
        STOP_ON_FIRST,
        /**
         * Collect outputs from every processor (optionally in parallel).
         */
        COLLECT_ALL
    }

    /**
     * Internal reusable pipeline implementation.
     */
    @Getter
    private static class Pipeline implements IProcessor {
        /**
         * Ordered chain of processors that form the pipeline.
         */
        private final List<IProcessor> chain;
        /**
         * Execution mode of the pipeline.
         */
        private final Mode mode;
        /**
         * Whether processors should be evaluated in parallel (only meaningful with COLLECT_ALL).
         */
        private final boolean parallel;
        /**
         * Executor used when parallel is true; otherwise null.
         */
        private final ExecutorService parallelExecutor;

        /**
         * Create a pipeline with given chain, mode and parallel flag.
         *
         * @param chain    the processor chain
         * @param mode     the mode of the pipeline
         * @param parallel whether to run processors in parallel (only meaningful with COLLECT_ALL)
         */
        Pipeline(List<IProcessor> chain, Mode mode, boolean parallel) {
            this.chain = new ArrayList<>(Objects.requireNonNull(chain, "chain"));
            this.mode = Objects.requireNonNull(mode, "mode");
            this.parallel = parallel;
            this.parallelExecutor = parallel ? Execs.newVirtualExecutor() : null;
        }

        Pipeline add(IProcessor p) {
            chain.add(Objects.requireNonNull(p, "processor"));
            return this;
        }

        @Override
        public List<MessageOut> handle(MessageIn in) {
            if (chain.isEmpty()) return Collections.emptyList();

            if (mode == Mode.STOP_ON_FIRST) {
                for (IProcessor p : chain) {
                    List<MessageOut> r = safeHandle(p, in);
                    if (!IProcessor.isEmpty(r)) {
                        return r; // short-circuit on first non-empty result
                    }
                }
                return Collections.emptyList();
            }

            // Mode.COLLECT_ALL
            if (!parallel) {
                List<MessageOut> outs = new ArrayList<>();
                for (IProcessor p : chain) {
                    List<MessageOut> r = safeHandle(p, in);
                    if (!IProcessor.isEmpty(r)) outs.addAll(r);
                }
                return outs;
            } else {
                // Parallel evaluation using virtual threads
                List<CompletableFuture<List<MessageOut>>> futures = chain.stream()
                        .map(p -> CompletableFuture.supplyAsync(() -> safeHandle(p, in), parallelExecutor))
                        .toList();

                List<MessageOut> outs = new ArrayList<>();
                for (CompletableFuture<List<MessageOut>> f : futures) {
                    try {
                        List<MessageOut> r = f.get();
                        if (!IProcessor.isEmpty(r)) outs.addAll(r);
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
         * Execute a single processor with exception isolation.
         */
        private List<MessageOut> safeHandle(IProcessor p, MessageIn in) {
            try {
                List<MessageOut> r = p.handle(in);
                return IProcessor.isEmpty(r) ? IProcessor.NO_OUTPUT : r;
            } catch (Throwable ex) {
                log.error("Processor {} error", p.getClass().getName(), ex);
                return IProcessor.NO_OUTPUT;
            }
        }
    }
}
