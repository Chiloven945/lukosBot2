package chiloven.lukosbot2.bootstrap;

import org.apache.logging.log4j.Logger;

import java.util.List;

public final class Lifecycle {
    private Lifecycle() {
    }

    /**
     * 关闭所有 AutoCloseable（忽略异常）
     */
    public static void shutdownQuietly(List<AutoCloseable> closeables, Logger log) {
        for (AutoCloseable c : closeables) {
            try {
                if (c != null) c.close();
            } catch (Exception e) {
                log.warn("关闭资源失败: {}", c, e);
            }
        }
    }
}
