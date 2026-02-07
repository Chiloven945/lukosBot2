package top.chiloven.lukosbot2.lifecycle.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import top.chiloven.lukosbot2.config.AppProperties;
import top.chiloven.lukosbot2.config.ProxyConfigProp;
import top.chiloven.lukosbot2.core.MessageDispatcher;
import top.chiloven.lukosbot2.core.MessageSenderHub;
import top.chiloven.lukosbot2.platform.ChatPlatform;
import top.chiloven.lukosbot2.platform.discord.DiscordReceiver;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "lukos.discord", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Log4j2
public class DiscordLifecycle implements SmartLifecycle, IPlatformAdapter {

    private final MessageDispatcher md;
    private final MessageSenderHub msh;
    private final AppProperties props;
    private final ProxyConfigProp proxyConfigProp;

    private final BaseCloseable closeable = new BaseCloseable();
    private volatile boolean running = false;

    @Override
    public List<AutoCloseable> start(MessageDispatcher md, MessageSenderHub msh) throws Exception {
        DiscordReceiver dc = new DiscordReceiver(props.getDiscord().getToken(), proxyConfigProp);
        dc.bind(md::receive);
        dc.start();
        msh.register(ChatPlatform.DISCORD, dc.sender());
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
            closeable.addAll(start(md, msh));
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
