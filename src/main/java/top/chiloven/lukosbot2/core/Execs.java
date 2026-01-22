package top.chiloven.lukosbot2.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility methods for Java 25 concurrency: provides a virtual-thread-per-task executor for
 * highly concurrent I/O or blocking tasks and a named daemon platform-thread factory for
 * single-threaded lanes; this is a non-instantiable helper class.
 */
public final class Execs {
    private Execs() {
    }

    /**
     * Creates a virtual-thread-per-task {@link java.util.concurrent.ExecutorService} with a default naming
     * pattern {@code "v-%02d"}, suitable when explicit naming is desired but a custom pattern is not needed.
     *
     * @return a per-task executor that creates named virtual threads using the default pattern
     */
    public static ExecutorService newVirtualExecutor() {
        return newVirtualExecutor("v-");
    }

    /**
     * Creates a virtual-thread-per-task {@link java.util.concurrent.ExecutorService} whose threads are explicitly
     * named using the given printf-style pattern (e.g., {@code "proc-%02d"} or {@code "send-%02d"}), enabling
     * clearer logs and diagnostics while preserving the lightweight concurrency of virtual threads.
     *
     * @param pattern the thread name pattern containing an optional {@code %d} placeholder that will be replaced
     *                by a monotonically increasing index starting at 1
     * @return a per-task executor that creates named virtual threads for each submitted task
     */
    public static ExecutorService newVirtualExecutor(String pattern) {
        AtomicLong seq = new AtomicLong(1);
        ThreadFactory vf = Thread.ofVirtual()
                .name(pattern, seq.getAndIncrement())
                .factory();
        return Executors.newThreadPerTaskExecutor(vf);
    }

    /**
     * Returns a {@link java.util.concurrent.ThreadFactory} that creates daemon platform threads named
     * using the given pattern (optionally containing {@code %d} for a monotonically increasing index),
     * useful for single-threaded “lane” executors and clearer diagnostics.
     *
     * @param pattern the thread name pattern, optionally including {@code %d} for an index
     * @return a thread factory producing named daemon platform threads
     */
    public static ThreadFactory platformNamedFactory(String pattern) {
        AtomicLong seq = new AtomicLong(1);
        return r -> {
            long n = seq.getAndIncrement();
            String name = String.format(pattern, n);
            return Thread.ofPlatform().daemon(true).name(name).unstarted(r);
        };
    }
}
