package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 可配置流水线：
 * - STOP_ON_FIRST（默认）：遇到首个非空输出即短路返回。
 * - COLLECT_ALL：收集所有处理器的输出；可并行执行（虚拟线程）。
 * <p>
 * 说明：Java 25 环境，直接使用 newVirtualThreadPerTaskExecutor()，不再做旧版本回退。
 */
public class PipelineProcessor implements Processor {
    private static final Logger log = LogManager.getLogger(PipelineProcessor.class);

    public enum Mode {STOP_ON_FIRST, COLLECT_ALL }

    private final List<Processor> chain = new ArrayList<>();
    private final Mode mode;
    private final boolean parallel;
    private final ExecutorService parallelExecutor; // 仅在 parallel=true 时使用

    public PipelineProcessor() {
        this(Mode.STOP_ON_FIRST, false, null);
    }

    public PipelineProcessor(Mode mode) {
        this(mode, false, null);
    }

    public PipelineProcessor(Mode mode, boolean parallel, ExecutorService parallelExecutor) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.parallel = parallel;
        this.parallelExecutor = parallel
                ? (parallelExecutor != null ? parallelExecutor : Executors.newVirtualThreadPerTaskExecutor())
                : null;
    }

    public PipelineProcessor add(Processor p) {
        chain.add(Objects.requireNonNull(p));
        return this;
    }

    @Override
    public List<MessageOut> handle(MessageIn in) {
        if (chain.isEmpty()) return Collections.emptyList();

        if (mode == Mode.STOP_ON_FIRST) {
            for (Processor p : chain) {
                List<MessageOut> r = safeHandle(p, in);
                if (r != null && !r.isEmpty()) {
                    return r; // 短路返回
                }
            }
            return Collections.emptyList();
        }

        // COLLECT_ALL
        if (!parallel) {
            List<MessageOut> outs = new ArrayList<>();
            for (Processor p : chain) {
                List<MessageOut> r = safeHandle(p, in);
                if (r != null && !r.isEmpty()) outs.addAll(r);
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
                    if (r != null && !r.isEmpty()) outs.addAll(r);
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

    private List<MessageOut> safeHandle(Processor p, MessageIn in) {
        long t0 = System.nanoTime();
        try {
            List<MessageOut> r = p.handle(in);
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            if (costMs >= 100) {
                LogManager.getLogger(p.getClass()).info("Processor {} took {} ms", p.getClass().getSimpleName(), costMs);
            }
            return (r == null || r.isEmpty()) ? Collections.emptyList() : r;
        } catch (Throwable ex) {
            log.error("Processor {} error", p.getClass().getName(), ex);
            return Collections.emptyList();
        }
    }
}
