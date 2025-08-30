package chiloven.lukosbot2;

import chiloven.lukosbot2.bootstrap.Boot;
import chiloven.lukosbot2.bootstrap.BootStepError;
import chiloven.lukosbot2.bootstrap.Lifecycle;
import chiloven.lukosbot2.config.Config;
import chiloven.lukosbot2.core.CommandRegistry;
import chiloven.lukosbot2.core.Pipeline;
import chiloven.lukosbot2.core.Router;
import chiloven.lukosbot2.core.SenderMux;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting lukosBot2...");
        List<AutoCloseable> closeables = new ArrayList<>();
        try {
            // 1) 配置
            Config cfg = Boot.loadConfigOrThrow();
            log.info("Configuration loaded.");

            // 2) 命令 & 流水线
            CommandRegistry registry = Boot.buildCommandsOrThrow(cfg);
            log.info("Commands registered: {}", registry.listCommands());

            Pipeline pipeline = Boot.buildPipelineOrThrow(cfg, registry);
            log.info("Message processing pipeline built.");

            // 3) 出站路由 & Router
            SenderMux senderMux = new SenderMux();
            Router router = new Router(pipeline, senderMux);
            log.info("Outbound routing set up.");

            // 4) 平台启动与注册（入站绑定、出站注册）
            closeables.addAll(Boot.startPlatformsOrThrow(cfg, router, senderMux, log));
            log.info("Chat platforms started.");

            // 5) 启动完成
            log.info("lukosBot2 started. Prefix: '{}'", cfg.prefix);

        } catch (BootStepError e) {
            // 分阶段退出码
            log.error(e.getMessage(), e.getCause());
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
