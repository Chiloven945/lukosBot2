package top.chiloven.lukosbot2.commands.bot.music

import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.bot.music.provider.IMusicProvider
import top.chiloven.lukosbot2.commands.bot.music.provider.SoundCloudMusicProvider
import top.chiloven.lukosbot2.commands.bot.music.provider.SpotifyMusicProvider
import top.chiloven.lukosbot2.config.CommandConfigProp
import top.chiloven.lukosbot2.core.command.bot.CommandSource
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["music"],
    havingValue = "true",
    matchIfMissing = true
)
class MusicCommand(ccp: CommandConfigProp) : IBotCommand {

    private val log = LogManager.getLogger(MusicCommand::class.java)

    private val spotify: IMusicProvider? = ccp.music.spotify.let { sp ->
        if (sp.enabled && sp.clientId.isNotBlank() && sp.clientSecret.isNotBlank()) SpotifyMusicProvider(
            sp.clientId,
            sp.clientSecret
        ) else null
    }
    private val soundCloud: IMusicProvider? = ccp.music.soundcloud.let { sc ->
        if (sc.enabled && sc.clientId.isNotBlank()) SoundCloudMusicProvider(sc.clientId) else null
    }

    private val commandDefinition = botCommand("music") {
        description = "从流媒体平台查询音乐信息"

        literal("link") {
            description = "通过链接解析歌曲"
            raw("link") { link -> runLink(source, link) }
            param("link", "Spotify 或 SoundCloud 链接")
        }

        raw("query", required = false) { query ->
            if (query.isBlank()) {
                sendUsage(source)
                return@raw
            }

            val tokens = query.split(" ", limit = 2)
            if (isPlatformToken(tokens[0])) {
                runSearch(source, tokens.getOrElse(1) { "" }, tokens[0])
            } else {
                runSearch(source, query, null)
            }
        }

        param("query", "搜索关键字（支持空格）")

        example(
            "music Never Gonna Give You Up",
            "music spotify Never Gonna Give You Up",
            "music link https://open.spotify.com/track/xxxxxxxx"
        )
    }

    override fun definition() = commandDefinition

    private fun runSearch(src: CommandSource, query: String, platformToken: String?): Int {
        return try {
            val provider = pickProvider(platformToken) ?: run {
                src.reply("音乐平台暂未配置或不可用。")
                return 0
            }
            val info = provider.searchTrack(query) ?: run {
                src.reply("没有找到匹配的歌曲。")
                return 0
            }

            src.reply(info.formatted())
            1
        } catch (e: Exception) {
            src.reply("查询失败：${e.message ?: "未知错误"}")
            log.warn("Music search failed: query='{}', platform='{}'", query, platformToken, e)
            0
        }
    }

    private fun runLink(src: CommandSource, link: String): Int {
        return try {
            val provider = detectProviderByLink(link) ?: run {
                src.reply("无法识别链接所属平台。")
                return 0
            }
            val info = provider.resolveLink(link) ?: run {
                src.reply("无法从链接解析到歌曲信息。")
                return 0
            }

            src.reply(info.formatted())
            1
        } catch (e: Exception) {
            src.reply("解析链接失败：${e.message ?: "未知错误"}")
            log.warn("Music link failed: link='{}'", link, e)
            0
        }
    }

    private fun pickProvider(platformToken: String?) = when (platformToken?.lowercase()) {
        null, "" -> spotify ?: soundCloud
        "s", "spotify" -> spotify
        "sc", "soundcloud" -> soundCloud
        else -> null
    }

    private fun detectProviderByLink(link: String?) = when {
        link == null -> null
        "open.spotify.com" in link.lowercase() -> spotify
        "soundcloud.com" in link.lowercase() -> soundCloud
        else -> null
    }

    private fun isPlatformToken(s: String?) =
        s?.lowercase() in setOf("spotify", "soundcloud", "sc", "s")

}
