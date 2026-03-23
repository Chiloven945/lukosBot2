package top.chiloven.lukosbot2.lifecycle;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.core.cli.CliCmdContext;
import top.chiloven.lukosbot2.core.cli.CliCmdProcessor;
import top.chiloven.lukosbot2.util.concurrent.Execs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;

@Service
@ConditionalOnProperty(prefix = "lukos.cli", name = "enabled", havingValue = "true")
@Log4j2
public class ConsoleLifecycle implements SmartLifecycle {

    private final CliCmdContext context;
    private final CliCmdProcessor processor;

    private volatile boolean running;
    private ExecutorService executor;

    public ConsoleLifecycle(CliCmdProcessor processor) {
        this.processor = processor;
        this.context = new CliCmdContext(System.out);
    }

    @Override
    public void start() {
        if (running) return;
        running = true;

        executor = Execs.newVirtualExecutor("cli-");
        executor.submit(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
                while (running) {
                    String line = reader.readLine();
                    if (line == null) break;
                    if (line.isBlank()) continue;

                    try {
                        processor.handle(line, context);
                    } catch (Exception e) {
                        log.warn("[Cli] Failed to execute command: {}", e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.warn("[Cli] Console lifecycle stopped unexpectedly: {}", e.getMessage(), e);
                }
            } finally {
                running = false;
            }
        });
    }

    @Override
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public int getPhase() {
        return 0;
    }

}
