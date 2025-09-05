package chiloven.lukosbot2.config;

public class Config {
    public String prefix = "/";
    public Telegram telegram = new Telegram();
    public OneBot onebot = new OneBot();
    public GitHub github = new GitHub();
    public Discord discord = new Discord();

    /**
     * GitHub Configuration
     */
    public static class GitHub {
        public String token = "";       // 可为空
    }

    /**
     * Telegram Configuration
     */
    public static class Telegram {
        public boolean enabled = false;
        public String botToken = "";
        public String botUsername = "";
    }

    /**
     * OneBot Configuration
     */
    public static class OneBot {
        public boolean enabled = false;
        public String wsUrl = "ws://127.0.0.1:6700";
        public String accessToken = null;
    }

    /**
     * Discord Configuration
     */
    public static class Discord {
        public boolean enabled = false;
        public String token = "";
    }
}
