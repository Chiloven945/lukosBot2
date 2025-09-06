package chiloven.lukosbot2;

import chiloven.lukosbot2.bootstrap.Boot;
import chiloven.lukosbot2.bootstrap.Lifecycle;
import chiloven.lukosbot2.config.Config;
import chiloven.lukosbot2.core.Router;
import chiloven.lukosbot2.core.SenderMux;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlatformManager {
    private static final Logger log = LogManager.getLogger(PlatformManager.class);
    private final Config cfg;
    private final Router router;
    private final SenderMux senderMux;
    private final List<AutoCloseable> closeable = new ArrayList<>();
    private volatile boolean started = false;

    public PlatformManager(Config cfg, Router router, SenderMux senderMux) {
        this.cfg = cfg;
        this.router = router;
        this.senderMux = senderMux;
    }

    /**
     * Start all configured chat platforms.
     * Is synchronized to prevent concurrent starts.
     */
    public synchronized void start() {
        if (started) return;
        closeable.addAll(Boot.startPlatforms(router, senderMux));
        log.info("Chat platforms started.");
        log.info("lukosBot2 started. Prefix: '{}'", cfg.prefix);
        started = true;
    }

    @PreDestroy
    public void shutdown() {
        Lifecycle.shutdownQuietly(closeable);
    }
}
