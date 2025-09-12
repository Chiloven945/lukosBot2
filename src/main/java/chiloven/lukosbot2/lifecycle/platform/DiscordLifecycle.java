package chiloven.lukosbot2.lifecycle.platform;

import chiloven.lukosbot2.config.AppProperties;
import chiloven.lukosbot2.core.Router;
import chiloven.lukosbot2.core.SenderMux;
import chiloven.lukosbot2.platforms.ChatPlatform;
import chiloven.lukosbot2.platforms.discord.DiscordReceiver;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "lukos.discord", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DiscordLifecycle implements SmartLifecycle, PlatformAdapter {

    private static final Logger log = LogManager.getLogger(DiscordLifecycle.class);

    private final Router router;
    private final SenderMux senderMux;
    private final AppProperties props;
    private final BaseCloseable closeable = new BaseCloseable();
    private volatile boolean running = false;

    @Override
    public List<AutoCloseable> start(Router router, SenderMux senderMux) throws Exception {
        DiscordReceiver dc = new DiscordReceiver(props.getDiscord().getToken());
        dc.bind(router::receive);
        dc.start();
        senderMux.register(ChatPlatform.DISCORD, dc.sender());
        closeable.add(dc);
        log.info("Discord ready");
        return List.of(closeable);
    }


    @Override
    public String name() {
        return "Discord";
    }

    @Override
    public void start() {
        if (running) return;
        try {
            closeable.addAll(start(router, senderMux));
            running = true;
            log.info("[{}] started (prefix='{}')", name(), props.getPrefix());
        } catch (Exception e) {
            throw new RuntimeException("Failed to start " + name(), e);
        }
    }

    @Override
    public void stop() {
        try {
            closeable.close();
        } finally {
            running = false;
        }
    }

    @Override
    public void stop(@NotNull Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 0;
    }
}
