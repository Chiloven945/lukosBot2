package top.chiloven.lukosbot2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "lukos")
public class AppProperties {

    ///  The prefix to trigger command processing, default is "/".
    private String prefix = "/";

    /**
     * The language for responses, default is "zh-cn".
     * Supported languages: en-us, zh-cn, zh-tw, ja-jp
     */
    private String language = "zh-cn";

    private Telegram telegram = new Telegram();
    private OneBot onebot = new OneBot();
    private Discord discord = new Discord();

    ///  Properties dealing with the Telegram bot.
    @Data
    public static class Telegram {
        ///  Whether to enable the Telegram bot platform, default is false.
        private boolean enabled = false;
        ///  The bot token provided by BotFather, default is empty.
        private String botToken = "";
        ///  The bot username, can be empty to let the library fetch it, default is empty.
        private String botUsername = "";
    }

    ///  Properties dealing with the OneBot platform.
    @Data
    public static class OneBot {
        ///  Whether to enable the OneBot platform, default is false.
        private boolean enabled = false;
        ///  The WebSocket URL of the OneBot server, default is "ws://
        private String wsUrl = "ws://127.0.0.1:6700";
        ///  The access token for authentication, default is empty.
        private String accessToken;
    }

    ///  Properties dealing with the JDA.
    @Data
    public static class Discord {
        /// Whether to enable the Discord platform, default is false.
        private boolean enabled = false;
        ///  The bot token provided by Discord Developer Portal, default is empty.
        private String token = "";
    }
}
