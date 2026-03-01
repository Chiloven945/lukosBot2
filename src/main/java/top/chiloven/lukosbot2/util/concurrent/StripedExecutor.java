package top.chiloven.lukosbot2.util.concurrent;

import top.chiloven.lukosbot2.util.MathUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides striped execution by hashing keys to single-thread “lanes” so tasks with the same key run serially while
 * different keys execute in parallel; lanes use named platform threads for easier diagnostics, and the number of lanes
 * is rounded up to a power of two for fast indexing.
 */
public final class StripedExecutor implements AutoCloseable {

    private final ExecutorService[] lanes;

    /**
     * Creates a striped executor with the given number of lanes (rounded to a power of two) and a thread-naming pattern
     * used for platform-thread lanes.
     *
     * @param stripes           the desired number of stripes (lane executors); values less than 1 are coerced to 1
     *                          before power-of-two rounding
     * @param threadNamePattern the thread name pattern (may contain {@code %d}) applied to lane threads
     */
    public StripedExecutor(int stripes, String threadNamePattern) {
        if (stripes <= 0) stripes = 1;
        stripes = MathUtils.ceilPowerOfTwo(stripes);
        this.lanes = new ExecutorService[stripes];
        for (int i = 0; i < stripes; i++) {
            lanes[i] = Executors.newSingleThreadExecutor(Execs.platformNamedFactory(threadNamePattern));
        }
    }

    /**
     * Submits a task to the lane determined by the provided key, ensuring tasks sharing the same key execute in
     * submission order on a single-threaded lane while tasks with different keys may run concurrently.
     *
     * @param key  the hashable partitioning key that determines the lane (e.g., chat ID)
     * @param task the runnable to execute on the selected lane
     */
    public void submit(Object key, Runnable task) {
        int idx = indexFor(key);
        lanes[idx].submit(task);
    }

    /**
     * Computes the stable lane index for the given key using a mixed hash and a power-of-two mask so the same key
     * consistently maps to the same single-threaded executor.
     *
     * @param key the partitioning key (may be {@code null})
     * @return the zero-based lane index within the internal executor array
     */
    private int indexFor(Object key) {
        int h = (key == null) ? 0 : key.hashCode();
        h ^= (h >>> 16);
        return (h & (lanes.length - 1));
    }

    public void shutdown() {
        close();
    }

    /**
     * Initiates an orderly shutdown of all lane executors so that previously submitted tasks execute to completion
     * while preventing new submissions.
     */
    @Override
    public void close() {
        for (ExecutorService lane : lanes) lane.shutdown();
    }

}
