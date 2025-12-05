package chiloven.lukosbot2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "lukos.commands")
public class CommandConfig {
    private Control control = new Control();
    private GitHub gitHub = new GitHub();
    private Music music = new Music();
    private Translate translate = new Translate();

    /// The control class for enabling/disabling the commands
    public static class Control {

    }

    /// GitHub related configurations.
    @Data
    public static class GitHub {
        /// The personal access token for GitHub API, default is empty. Can be applied to increase rate limit.
        private String token = "";
    }

    /// Music related configurations. Client ID or token needs to be obtained manually.
    @Data
    public static class Music {
        private Spotify spotify = new Spotify();
        private SoundCloud soundcloud = new SoundCloud();

        /// Setting related to the platform Spotify
        @Data
        public static class Spotify {
            /// Whether to enable Spotify provider.
            private boolean enabled = false;
            /// The Client ID obtained from Spotify Developer.
            private String clientId = "";
            /// The Client Secret obtained from Spotify Developer.
            private String clientSecret = "";
        }

        /// Setting related to the platform SoundCloud
        @Data
        public static class SoundCloud {
            /// Whether to enable SoundCloud provider.
            private boolean enabled = false;
            private String clientId = "";
        }
    }

    /// Translate related configurations.
    @Data
    public static class Translate {
        /// The default language to translate, without a specific language code.
        private String defaultLang = "zh-CN";
        /// The URL of the translating server
        private String url = "";
    }
}
