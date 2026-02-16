package top.chiloven.lukosbot2.commands.impl.music

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.commands.impl.music.provider.IMusicProvider
import top.chiloven.lukosbot2.commands.impl.music.provider.SoundCloudMusicProvider
import top.chiloven.lukosbot2.commands.impl.music.provider.SpotifyMusicProvider
import top.chiloven.lukosbot2.config.CommandConfigProp
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal
import top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument

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
        val ok = sp.isEnabled &&
                !sp.clientId.isNullOrBlank() &&
                !sp.clientSecret.isNullOrBlank()
        if (ok) SpotifyMusicProvider(sp.clientId, sp.clientSecret) else null
    }

    private val soundCloud: IMusicProvider? = ccp.music.soundcloud.let { sc ->
        val ok = sc.isEnabled && !sc.clientId.isNullOrBlank()
        if (ok) SoundCloudMusicProvider(sc.clientId) else null
    }

    override fun name(): String = "music"

    override fun description(): String = "从流媒体平台查询音乐信息"

    override fun usage(): UsageNode {
        return UsageNode.root(name())
            .description(description())
            .syntax("搜索歌曲（默认平台）", UsageNode.arg("query"))
            .syntax(
                "在指定平台搜索歌曲",
                UsageNode.oneOf(UsageNode.lit("spotify"), UsageNode.lit("soundcloud"), UsageNode.lit("sc")),
                UsageNode.arg("query")
            )
            .subcommand("link", "通过链接解析歌曲") { b ->
                b.syntax("解析歌曲链接", UsageNode.arg("link"))
                    .param("link", "Spotify 或 SoundCloud 链接")
            }
            .param("query", "搜索关键字（支持空格）")
            .example(
                "music Never Gonna Give You Up",
                "music spotify Never Gonna Give You Up",
                "music link https://open.spotify.com/track/xxxxxxxx"
            )
            .build()
    }

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    sendUsage(ctx.source)
                    1
                }
                .then(
                    literal("link")
                        .then(
                            argument("link", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    runLink(ctx.source, StringArgumentType.getString(ctx, "link"))
                                })
                )
                .then(
                    argument("first", StringArgumentType.word())
                        .executes { ctx ->
                            runSearch(
                                src = ctx.source,
                                query = StringArgumentType.getString(ctx, "first"),
                                platformToken = null
                            )
                        }
                        .then(
                            argument("rest", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    val src = ctx.source
                                    val first = StringArgumentType.getString(ctx, "first")
                                    val rest = StringArgumentType.getString(ctx, "rest")

                                    if (isPlatformToken(first)) {
                                        // /music <platform> <query>
                                        runSearch(src, rest, first)
                                    } else {
                                        // /music <query-with-space>
                                        runSearch(src, "$first $rest", null)
                                    }
                                }
                        )
                )
        )
    }

    private fun runSearch(src: CommandSource, query: String, platformToken: String?): Int {
        return try {
            val provider = pickProvider(platformToken)
            if (provider == null) {
                src.reply("未配置可用的音乐平台（Spotify / SoundCloud），请在 config/application.yml 中设置 lukos.music.*")
                return 0
            }

            val info = provider.searchTrack(query)
            if (info == null) {
                src.reply("没有找到匹配的歌曲。")
                return 0
            }

            src.reply(info.formatted())
            1
        } catch (e: Exception) {
            src.reply("查询失败：${e.message ?: "unknown error"}")
            log.warn("Music search failed: query='{}', platform='{}'", query, platformToken, e)
            0
        }
    }

    private fun runLink(src: CommandSource, link: String): Int {
        return try {
            val provider = detectProviderByLink(link)
            if (provider == null) {
                src.reply("无法识别链接所属平台，仅支持 Spotify / SoundCloud。")
                return 0
            }

            val info = provider.resolveLink(link)
            if (info == null) {
                src.reply("无法从链接解析到歌曲信息。")
                return 0
            }

            src.reply(info.formatted())
            1
        } catch (e: Exception) {
            src.reply("解析链接失败：${e.message ?: "unknown error"}")
            log.warn("Music link failed: link='{}'", link, e)
            0
        }
    }

    private fun pickProvider(platformToken: String?): IMusicProvider? {
        if (platformToken.isNullOrBlank()) {
            return spotify ?: soundCloud
        }
        return when (platformToken.lowercase()) {
            "s", "spotify" -> spotify
            "sc", "soundcloud" -> soundCloud
            else -> null
        }
    }

    private fun detectProviderByLink(link: String?): IMusicProvider? {
        val s = link?.lowercase().orEmpty()
        return when {
            "open.spotify.com" in s -> spotify
            "soundcloud.com" in s -> soundCloud
            else -> null
        }
    }

    private fun isPlatformToken(s: String?): Boolean {
        val p = s?.lowercase() ?: return false
        return p == "spotify" || p == "soundcloud" || p == "sc" || p == "s"
    }
}
