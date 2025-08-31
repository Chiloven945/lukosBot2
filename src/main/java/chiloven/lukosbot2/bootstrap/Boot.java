package chiloven.lukosbot2.bootstrap;

import chiloven.lukosbot2.commands.EchoCommand;
import chiloven.lukosbot2.commands.HelpCommand;
import chiloven.lukosbot2.commands.PingCommand;
import chiloven.lukosbot2.config.Config;
import chiloven.lukosbot2.core.*;
import chiloven.lukosbot2.model.ChatPlatform;
import chiloven.lukosbot2.platforms.onebot.OneBotReceiver;
import chiloven.lukosbot2.platforms.telegram.TelegramReceiver;
import chiloven.lukosbot2.util.json.JsonUtil;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bootstrap helper class
 */
public final class Boot {
    private Boot() {
    }

    public static Config loadConfigOrThrow() {
        try {
            return JsonUtil.readJson(Path.of("config", "config.json"), Config.class);
        } catch (Exception e) {
            throw new BootStepError(1, "Failed to read the configuration: config/config.json", e);
        }
    }

    public static CommandRegistry buildCommandsOrThrow(Config cfg) {
        try {
            CommandRegistry registry = new CommandRegistry()
                    .add(new PingCommand())
                    .add(new EchoCommand())
                    .add(new chiloven.lukosbot2.commands.github.GitHubCommand(cfg.github.token));
            registry.add(new HelpCommand(registry, cfg.prefix));
            return registry;
        } catch (Exception e) {
            throw new BootStepError(2, "Failed to register commands", e);
        }
    }

    public static Pipeline buildPipelineOrThrow(Config cfg, CommandRegistry registry) {
        try {
            CommandProcessor cmd = new CommandProcessor(cfg.prefix, registry);
            return new Pipeline()
                    .add(new PrefixGuardProcessor(cfg.prefix))
                    .add(cmd);
        } catch (Exception e) {
            throw new BootStepError(3, "Failed to build the message processing pipeline", e);
        }
    }

    /**
     * Start platform receivers and register their senders to the mux; return a list of closeable resources
     */
    public static List<AutoCloseable> startPlatformsOrThrow(
            Config cfg, Router router, SenderMux senderMux, Logger log) {
        List<AutoCloseable> closeables = new ArrayList<>();
        try {
            if (cfg.telegram.enabled) {
                TelegramReceiver tg = new TelegramReceiver(cfg.telegram.botToken, cfg.telegram.botUsername);
                tg.bind(router::receive);
                tg.start();
                senderMux.register(ChatPlatform.TELEGRAM, tg.sender());
                closeables.add(tg);
                log.info("Telegram ready as @{}", cfg.telegram.botUsername);
            }
            if (cfg.onebot.enabled) {
                OneBotReceiver ob = new OneBotReceiver(cfg.onebot.wsUrl, cfg.onebot.accessToken);
                ob.bind(router::receive);
                ob.start();
                senderMux.register(ChatPlatform.ONEBOT, ob.sender());
                closeables.add(ob);
                log.info("OneBot ready {}", cfg.onebot.wsUrl);
            }
            if (!cfg.telegram.enabled && !cfg.onebot.enabled) {
                throw new BootStepError(6, "No platform enabled (please enable telegram/onebot in config/config.json)", null);
            }
            return closeables;
        } catch (BootStepError e) {
            throw e;
        } catch (Exception e) {
            throw new BootStepError(5, "Failed to start chat platforms", e);
        }
    }
}
