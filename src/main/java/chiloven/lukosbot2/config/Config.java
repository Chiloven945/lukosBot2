package chiloven.lukosbot2.config;

public class Config {
    private static Config instance;
    public int maxTimeoutMs = 4000;
    //TODO: Language

    public Telegram telegram = new Telegram();
    public OneBot onebot = new OneBot();
    public GitHub github = new GitHub();
    public Discord discord = new Discord();

    /**
     * Get the global configuration instance.
     *
     * @return the configuration instance
     * @throws IllegalStateException if the configuration has not been initialized
     */
    public static Config get() {
        if (instance == null) {
            throw new IllegalStateException("Config not initialized.");
        }
        return instance;
    }

    public String prefix = "/";

    /**
     * Set the global configuration instance.
     *
     * @param cfg the configuration to set
     */
    public static void set(Config cfg) {
        instance = cfg;
    }


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
