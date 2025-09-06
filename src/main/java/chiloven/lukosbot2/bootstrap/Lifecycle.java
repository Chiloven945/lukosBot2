package chiloven.lukosbot2.bootstrap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public final class Lifecycle {
    private static final Logger log = LogManager.getLogger(Lifecycle.class);

    private Lifecycle() {
    }

    /**
     * Close multiple AutoCloseable resources quietly, logging any exceptions.
     */
    public static void shutdownQuietly(List<AutoCloseable> closeable) {
        for (AutoCloseable c : closeable) {
            try {
                if (c != null) c.close();
            } catch (Exception e) {
                log.warn("Failed to close the resources: {}", c, e);
            }
        }
    }
}
