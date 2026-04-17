package top.chiloven.lukosbot2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "lukos")
public class AppProperties {

    /// The prefix to trigger command processing, default is "/".
    private String prefix = "/";

    /**
     * The language for responses, default is "zh-cn". Supported languages: en-us, zh-cn, zh-tw, ja-jp
     */
    private String language = "zh-cn";

    private Telegram telegram = new Telegram();
    private OneBot onebot = new OneBot();
    private Discord discord = new Discord();
    private Cli cli = new Cli();
    private Security security = new Security();
    private Policy policy = new Policy();

    /// Properties dealing with the Telegram bot.
    @Data
    public static class Telegram {

        /// Whether to enable the Telegram bot platform, default is false.
        private boolean enabled = false;
        /// The bot token provided by BotFather, default is empty.
        private String botToken = "";
        /// The bot username, can be empty to let the library fetch it, default is empty.
        private String botUsername = "";

    }

    /// Properties dealing with the OneBot platform.
    @Data
    public static class OneBot {

        /// Whether to enable the OneBot platform, default is false.
        private boolean enabled = false;
        /// The WebSocket URL of the OneBot server, default is "ws://
        private String wsUrl = "ws://127.0.0.1:6700";
        /// The access token for authentication, default is empty.
        private String accessToken;

    }

    /// Properties dealing with the JDA.
    @Data
    public static class Discord {

        /// Whether to enable the Discord platform, default is false.
        private boolean enabled = false;
        /// The bot token provided by Discord Developer Portal, default is empty.
        private String token = "";

    }

    @Data
    public static class Cli {

        /// Whether to enable the Command Line Interface feature to control the bot.
        private boolean enabled = true;

    }


    @Data
    public static class Policy {

        /**
         * Runtime policy rules evaluated against the current chat context.
         *
         * <p>Typical uses:</p>
         * <ul>
         *   <li>Disable a command on a whole platform.</li>
         *   <li>Disable a command outside private chats.</li>
         *   <li>Constrain feature values such as e621 rating by chat context.</li>
         * </ul>
         */
        private List<Rule> rules = new ArrayList<>();

        @Data
        public static class Rule {

            private String id = "";
            private int priority = 0;
            private Match when = new Match();
            private List<String> disableCommands = new ArrayList<>();
            private Map<String, List<String>> allowValues = new LinkedHashMap<>();

        }

        @Data
        public static class Match {

            private String platform;
            private Boolean privateChat;
            private Boolean group;
            private Boolean nsfw;
            private Long chatId;
            private Long userId;

        }

    }

    @Data
    public static class Security {

        /**
         * Bootstrap bot admins defined in config.
         *
         * <p>Example:</p>
         * <pre>
         * bootstrapBotAdmins:
         *   telegram: [123456789]
         *   discord: [987654321]
         * </pre>
         */
        private Map<String, List<Long>> bootstrapBotAdmins = new LinkedHashMap<>();

    }

}
