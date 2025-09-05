package chiloven.lukosbot2.bootstrap;

import chiloven.lukosbot2.commands.EchoCommand;
import chiloven.lukosbot2.commands.HelpCommand;
import chiloven.lukosbot2.commands.PingCommand;
import chiloven.lukosbot2.commands.WikiCommand;
import chiloven.lukosbot2.commands.github.GitHubCommand;
import chiloven.lukosbot2.config.Config;
import chiloven.lukosbot2.core.*;
import chiloven.lukosbot2.platforms.ChatPlatform;
import chiloven.lukosbot2.platforms.discord.DiscordReceiver;
import chiloven.lukosbot2.platforms.onebot.OneBotReceiver;
import chiloven.lukosbot2.platforms.telegram.TelegramReceiver;
import chiloven.lukosbot2.util.json.JsonUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bootstrap helper class
 */
public final class Boot {
    private Boot() {
    }

    public static Config loadConfigOrThrow(Logger log) {
        Path dir = Path.of("config");
        Path file = dir.resolve("config.json");
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            // 1) Default config as JsonObject
            Config defCfg = new Config();
            JsonObject def = JsonUtil.toJsonTree(defCfg);

            JsonObject current;
            boolean created = false;

            // 2) Read existing or create new
            if (!Files.exists(file)) {
                String json = JsonUtil.toJson(defCfg);
                Files.writeString(file, json, StandardCharsets.UTF_8);
                current = def.deepCopy();
                created = true;
                log.info("Unable to find config file, a new one has been created at: {}", file.toString());
            } else {
                String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (raw.isEmpty()) raw = "{}";
                JsonElement parsed = JsonParser.parseString(raw);
                current = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            }

            // 3) Deep merge: use current to override def, resulting in merged (= def + missing filled)
            JsonObject merged = def.deepCopy();
            deepMergeInto(merged, current);

            // 4) If there are changes (merged != current), write back to file
            boolean patched = !JsonUtil.normalizePretty(current).equals(JsonUtil.normalizePretty(merged));
            if (patched && !created) {
                Files.writeString(file, JsonUtil.toJson(merged), StandardCharsets.UTF_8);
                log.info("Missing keys have been added and written back to: {}", file);
            }

            // 5) Return the final Config object
            return JsonUtil.fromJsonTree(merged, Config.class);

        } catch (Exception e) {
            throw new BootStepError(1, "加载/创建配置失败: " + file, e);
        }
    }

    /**
     * Deep merge src into dst (modifies dst).
     * If both dst and src have a value for the same key and both values are Json
     * objects, merge them recursively. Otherwise, overwrite dst's value with src's value.
     * This does not handle Json arrays specially; src's value
     * will overwrite dst's value if they are arrays.
     *
     * @param dst the destination JsonObject to merge into
     * @param src the source JsonObject to merge from
     */
    private static void deepMergeInto(JsonObject dst, JsonObject src) {
        for (String key : src.keySet()) {
            JsonElement srcVal = src.get(key);
            // If dst does not have the key, simply add it
            if (!dst.has(key)) {
                dst.add(key, srcVal);
                continue;
            }

            JsonElement dstVal = dst.get(key);
            // If both values are JsonObjects, merge them recursively
            if (srcVal != null && srcVal.isJsonObject() && dstVal != null && dstVal.isJsonObject()) {
                deepMergeInto(dstVal.getAsJsonObject(), srcVal.getAsJsonObject());
            } else {
                dst.add(key, srcVal);
            }
        }
    }

    public static CommandRegistry buildCommandsOrThrow(Config cfg) {
        try {
            CommandRegistry registry = new CommandRegistry()
                    .add(
                            new PingCommand(),
                            new EchoCommand(),
                            new GitHubCommand(cfg.github.token),
                            new WikiCommand()
                    );
            registry.add(new HelpCommand(registry, cfg.prefix));
            return registry;
        } catch (Exception e) {
            throw new BootStepError(2, "Failed to register commands", e);
        }
    }

    public static PipelineProcessor buildPipelineOrThrow(Config cfg, CommandRegistry registry) {
        try {
            CommandProcessor cmd = new CommandProcessor(cfg.prefix, registry);
            return new PipelineProcessor()
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
                log.info("OneBot ready on {}", cfg.onebot.wsUrl);
            }
            if (cfg.discord.enabled) {
                DiscordReceiver dc = new DiscordReceiver(cfg.discord.token);
                dc.bind(router::receive);
                dc.start();
                senderMux.register(ChatPlatform.DISCORD, dc.sender());
                closeables.add(dc);
                log.info("Discord ready");
            }
            if (!(cfg.telegram.enabled || cfg.onebot.enabled || cfg.discord.enabled)) {
                throw new BootStepError(6, "No platform enabled (please enable telegram/onebot/discord in config/config.json)", null);
            }
            return closeables;
        } catch (BootStepError e) {
            throw e;
        } catch (Exception e) {
            throw new BootStepError(5, "Failed to start chat platforms", e);
        }
    }
}
