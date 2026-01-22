package top.chiloven.lukosbot2.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides striped execution by hashing keys to single-thread “lanes” so tasks with the same key run serially
 * while different keys execute in parallel; lanes use named platform threads for easier diagnostics, and the
 * number of lanes is rounded up to a power of two for fast indexing.
 */
final class StripedExecutor implements AutoCloseable {
    private final ExecutorService[] lanes;

    /**
     * Creates a striped executor with the given number of lanes (rounded to a power of two) and a thread-naming
     * pattern used for platform-thread lanes.
     *
     * @param stripes           the desired number of stripes (lane executors); values less than 1 are coerced to 1 before
     *                          power-of-two rounding
     * @param threadNamePattern the thread name pattern (may contain {@code %d}) applied to lane threads
     */
    StripedExecutor(int stripes, String threadNamePattern) {
        if (stripes <= 0) stripes = 1;
        stripes = toPowerOfTwo(stripes);
        this.lanes = new ExecutorService[stripes];
        for (int i = 0; i < stripes; i++) {
            lanes[i] = Executors.newSingleThreadExecutor(Execs.platformNamedFactory(threadNamePattern));
        }
    }

    /**
     * Rounds the provided integer up to the next power of two (or returns 1 if the input is less than or equal to 1)
     * to enable efficient lane indexing via bitwise operations.
     *
     * @param n the requested stripe count
     * @return the smallest power of two greater than or equal to {@code n}
     */
    private static int toPowerOfTwo(int n) {
        int x = 1;
        while (x < n) x <<= 1;
        return x;
    }

    /**
     * Submits a task to the lane determined by the provided key, ensuring tasks sharing the same key execute in
     * submission order on a single-threaded lane while tasks with different keys may run concurrently.
     *
     * @param key  the hashable partitioning key that determines the lane (e.g., chat ID)
     * @param task the runnable to execute on the selected lane
     */
    void submit(Object key, Runnable task) {
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

    /**
     * Initiates an orderly shutdown of all lane executors so that previously submitted tasks execute to completion
     * while preventing new submissions.
     */
    @Override
    public void close() {
        for (ExecutorService lane : lanes) lane.shutdown();
    }
}
