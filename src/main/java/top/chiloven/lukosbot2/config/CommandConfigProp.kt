package top.chiloven.lukosbot2.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "lukos.commands")
data class CommandConfigProp(
    var control: Control = Control,
    var gitHub: GitHub = GitHub(),
    var music: Music = Music(),
    var translate: Translate = Translate(),
    var twentyFour: TwentyFour = TwentyFour(),
) {

    /**
     * The control class for enabling/disabling the commands.
     *
     * It is empty for now, so it is a data object instead of a fake data class with meaningless fields.
     */
    data object Control

    /**
     * GitHub related configurations.
     */
    data class GitHub(
        /**
         * The personal access token for GitHub API, default is empty. Can be applied to increase rate limit.
         */
        var token: String = "",
    )

    /**
     * Music related configurations. Client ID or token needs to be obtained manually.
     */
    data class Music(
        var spotify: Spotify = Spotify(),
        var soundcloud: SoundCloud = SoundCloud(),
    ) {

        /**
         * Setting related to the platform Spotify.
         */
        data class Spotify(
            /**
             * Whether to enable Spotify provider.
             */
            var enabled: Boolean = false,

            /**
             * The Client ID obtained from Spotify Developer.
             */
            var clientId: String = "",

            /**
             * The Client Secret obtained from Spotify Developer.
             */
            var clientSecret: String = "",
        )

        /**
         * Setting related to the platform SoundCloud.
         */
        data class SoundCloud(
            /**
             * Whether to enable SoundCloud provider.
             */
            var enabled: Boolean = false,

            var clientId: String = "",
        )
    }

    /**
     * Translate related configurations.
     */
    data class Translate(
        /**
         * The default language to translate, without a specific language code.
         */
        var defaultLang: String = "zh-CN",

        /**
         * The URL of the translating server.
         */
        var url: String = "",
    )

    /**
     * TwentyFour game related configurations.
     */
    data class TwentyFour(
        var timeLimit: Long = 300000,
    )

}
