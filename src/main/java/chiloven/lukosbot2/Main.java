package chiloven.lukosbot2;

import chiloven.lukosbot2.bootstrap.Boot;
import chiloven.lukosbot2.bootstrap.BootStepError;
import chiloven.lukosbot2.bootstrap.Lifecycle;
import chiloven.lukosbot2.config.Config;
import chiloven.lukosbot2.core.CommandRegistry;
import chiloven.lukosbot2.core.PipelineProcessor;
import chiloven.lukosbot2.core.Router;
import chiloven.lukosbot2.core.SenderMux;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        System.out.println("""
                 __       __  __  __  __   _____   ____    ____     _____   ______    ___    \s
                /\\ \\     /\\ \\/\\ \\/\\ \\/\\ \\ /\\  __`\\/\\  _`\\ /\\  _`\\  /\\  __`\\/\\__  _\\ /'___`\\  \s
                \\ \\ \\    \\ \\ \\ \\ \\ \\ \\/'/'\\ \\ \\/\\ \\ \\,\\L\\_\\ \\ \\L\\ \\\\ \\ \\/\\ \\/_/\\ \\//\\_\\ /\\ \\ \s
                 \\ \\ \\  __\\ \\ \\ \\ \\ \\ , <  \\ \\ \\ \\ \\/_\\__ \\\\ \\  _ <'\\ \\ \\ \\ \\ \\ \\ \\\\/_/// /__\s
                  \\ \\ \\L\\ \\\\ \\ \\_\\ \\ \\ \\\\`\\ \\ \\ \\_\\ \\/\\ \\L\\ \\ \\ \\L\\ \\\\ \\ \\_\\ \\ \\ \\ \\  // /_\\ \\
                   \\ \\____/ \\ \\_____\\ \\_\\ \\_\\\\ \\_____\\ `\\____\\ \\____/ \\ \\_____\\ \\ \\_\\/\\______/
                    \\/___/   \\/_____/\\/_/\\/_/ \\/_____/\\/_____/\\/___/   \\/_____/  \\/_/\\/_____/\s""");
        log.info("Starting lukosBot2...");
        List<AutoCloseable> closeables = new ArrayList<>();
        try {
            // 1) Configuration
            Config cfg = Boot.loadConfigOrThrow(log);
            log.info("Configuration loaded.");

            // 2) Command & Pipeline
            CommandRegistry registry = Boot.buildCommandsOrThrow(cfg);
            log.info("Commands registered: {}", registry.listCommands());

            PipelineProcessor pipeline = Boot.buildPipelineOrThrow(cfg, registry);
            log.info("Message processing pipeline built.");

            // 3) SenderMux & Router
            SenderMux senderMux = new SenderMux();
            Router router = new Router(pipeline, senderMux);
            log.info("Outbound routing set up.");

            // 4) Platforms registration & startup
            closeables.addAll(Boot.startPlatformsOrThrow(cfg, router, senderMux, log));
            log.info("Chat platforms started.");

            // 5) Startup complete
            log.info("lukosBot2 started. Prefix: '{}'", cfg.prefix);

        } catch (BootStepError e) {
            // Shutdown and exit with specific code
            log.fatal(e.getMessage(), e.getCause());
            Lifecycle.shutdownQuietly(closeables, log);
            System.exit(e.code());
            return;
        } catch (Throwable t) {
            log.fatal("Unexpected fatal error during startup", t);
            Lifecycle.shutdownQuietly(closeables, log);
            System.exit(99);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> Lifecycle.shutdownQuietly(closeables, log)));
    }
}
