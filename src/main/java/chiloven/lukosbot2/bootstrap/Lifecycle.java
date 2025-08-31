package chiloven.lukosbot2.bootstrap;

import org.apache.logging.log4j.Logger;

import java.util.List;

public final class Lifecycle {
    private Lifecycle() {
    }

    /**
     * Close multiple AutoCloseable resources quietly, logging any exceptions.
     */
    public static void shutdownQuietly(List<AutoCloseable> closeables, Logger log) {
        for (AutoCloseable c : closeables) {
            try {
                if (c != null) c.close();
            } catch (Exception e) {
                log.warn("Failed to close the resources: {}", c, e);
            }
        }
    }
}
