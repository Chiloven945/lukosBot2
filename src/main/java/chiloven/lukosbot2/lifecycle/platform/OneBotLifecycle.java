package chiloven.lukosbot2.lifecycle.platform;

import chiloven.lukosbot2.config.AppProperties;
import chiloven.lukosbot2.core.MessageDispatcher;
import chiloven.lukosbot2.core.MessageSenderHub;
import chiloven.lukosbot2.platform.ChatPlatform;
import chiloven.lukosbot2.platform.onebot.OneBotReceiver;
import com.mikuac.shiro.core.BotContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "lukos.onebot", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Log4j2
public class OneBotLifecycle implements SmartLifecycle, PlatformAdapter {

    private final MessageDispatcher md;
    private final MessageSenderHub msh;
    private final AppProperties props;
    private final BotContainer botContainer;

    private final BaseCloseable closeable = new BaseCloseable();
    private volatile boolean running = false;

    @Override
    public List<AutoCloseable> start(MessageDispatcher md, MessageSenderHub msh) throws Exception {
        OneBotReceiver ob = new OneBotReceiver(botContainer);
        ob.start();
        msh.register(ChatPlatform.ONEBOT, ob.sender());
        closeable.add(ob);

        log.info("OneBot (Shiro) ready (wsUrl={})", props.getOnebot().getWsUrl());
        return List.of(closeable);
    }

    @Override
    public String name() {
        return "OneBot";
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
