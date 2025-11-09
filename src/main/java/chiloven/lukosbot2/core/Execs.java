package chiloven.lukosbot2.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility methods for Java 25 concurrency: provides a virtual-thread-per-task executor for
 * highly concurrent I/O or blocking tasks and a named daemon platform-thread factory for
 * single-threaded lanes; this is a non-instantiable helper class.
 */
final class Execs {
    private Execs() {
    }

    /**
     * Returns a virtual-thread-per-task {@link java.util.concurrent.ExecutorService} where each
     * submitted task runs in its own virtual thread, suitable for massively concurrent, potentially
     * blocking workloads.
     *
     * @return a new virtual-thread-per-task executor service
     */
    static ExecutorService newVirtualExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Returns a {@link java.util.concurrent.ThreadFactory} that creates daemon platform threads named
     * using the given pattern (optionally containing {@code %d} for a monotonically increasing index),
     * useful for single-threaded “lane” executors and clearer diagnostics.
     *
     * @param pattern the thread name pattern, optionally including {@code %d} for an index
     * @return a thread factory producing named daemon platform threads
     */
    static ThreadFactory platformNamedFactory(String pattern) {
        AtomicLong seq = new AtomicLong(1);
        return r -> Thread.ofPlatform()
                .daemon(true)
                .name(pattern, seq.getAndIncrement())
                .unstarted(r);
    }
}
