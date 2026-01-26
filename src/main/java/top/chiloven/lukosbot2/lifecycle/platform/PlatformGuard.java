package top.chiloven.lukosbot2.lifecycle.platform;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * If none of the three platform adapters are enabled, fail fast during startup (semantically equivalent to the check at the end of Boot.startPlatforms)
 */
@Component
public class PlatformGuard implements SmartLifecycle {
    private final List<PlatformAdapter> adapters;
    private volatile boolean running = false;

    public PlatformGuard(ObjectProvider<List<PlatformAdapter>> provider) {
        this.adapters = provider.getIfAvailable(List::of);
    }

    @Override
    public void start() throws IllegalStateException {
        if (adapters.isEmpty()) {
            throw new IllegalStateException("No platform enabled (set lukos.telegram/onebot/discord.enabled in application.yml)");
        }
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void stop(@NotNull Runnable cb) {
        try {
            stop();
        } finally {
            cb.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return -100;
    }
}
