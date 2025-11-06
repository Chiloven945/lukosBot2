package chiloven.lukosbot2.core;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 条带化执行器：把任务按 key 映射到单线程 lane。
 * - 不同 key 可并行
 * - 相同 key 在同一 lane 内串行，保证顺序
 */
final class StripedExecutor implements AutoCloseable {
    private final ExecutorService[] lanes;

    StripedExecutor(int stripes, String threadNamePattern) {
        if (stripes <= 0) stripes = 1;
        stripes = toPowerOfTwo(stripes);
        this.lanes = new ExecutorService[stripes];
        for (int i = 0; i < stripes; i++) {
            lanes[i] = Executors.newSingleThreadExecutor(new Execs.NamedFactory(threadNamePattern));
        }
    }

    private static int toPowerOfTwo(int n) {
        int x = 1;
        while (x < n) x <<= 1;
        return x;
    }

    void submit(Object key, Runnable task) {
        Objects.requireNonNull(task, "task");
        int idx = indexFor(key);
        lanes[idx].submit(task);
    }

    private int indexFor(Object key) {
        int h = (key == null) ? 0 : key.hashCode();
        h ^= (h >>> 16);
        return (h & (lanes.length - 1));
    }

    @Override
    public void close() {
        for (ExecutorService lane : lanes) {
            lane.shutdown();
        }
    }
}
