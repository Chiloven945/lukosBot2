package top.chiloven.lukosbot2.commands.impl.bilibili

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.long
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.StringUtils.formatNum
import top.chiloven.lukosbot2.util.StringUtils.formatTime
import top.chiloven.lukosbot2.util.StringUtils.truncate
import top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal
import top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpClient.Redirect
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["bilibili"],
    havingValue = "true",
    matchIfMissing = true
)
class BilibiliCommand : IBotCommand {
    private val log = LogManager.getLogger(BilibiliCommand::class.java)

    private companion object {
        private val HTTP: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(Redirect.NEVER)
            .build()

        private val BV_PATTERN = Regex("""(?i)\bBV([0-9A-Za-z]{10})\b""")
        private val AV_PATTERN = Regex("""(?i)\bAV?(\d+)\b""")

        @Suppress("unused")
        private val B23_PATTERN = Regex("""https?://b23\.tv/([0-9A-Za-z]+)\b""")

        private const val UA = "Mozilla/5.0 (compatible; ${Constants.UA}; +https://bilibili.com)"
    }

    override fun name(): String = "bilibili"
    override fun description(): String = "查看B站视频（支持 AV/BV/短链）"

    override fun usage(): UsageNode {
        val target = UsageNode.oneOf(UsageNode.arg("code"), UsageNode.arg("link"))
        return UsageNode.root(name())
            .description(description())
            .syntax("查询 B 站视频信息（可选 -i 获取更详细的信息）", target, UsageNode.opt(UsageNode.lit("-i")))
            .param("code", "视频编号（AV/BV）")
            .param("link", "视频链接或 b23 短链")
            .option("-i", "输出更多信息")
            .example(
                "bilibili BV1GJ411x7h7",
                "bilibili av170001",
                "bilibili https://b23.tv/xxxxxx -i"
            )
            .build()
    }

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .then(
                    argument("id", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val raw = StringArgumentType.getString(ctx, "id")
                            run(ctx.source, raw, detailed = false)
                        }
                        .then(
                            literal("-i").executes { ctx ->
                                val raw = StringArgumentType.getString(ctx, "id")
                                run(ctx.source, raw, detailed = true)
                            }
                        )
                )
        )
    }

    private fun run(src: CommandSource, rawInput: String, detailed: Boolean): Int {
        log.info(
            "BilibiliCommand invoked by {} with input='{}', detailed={}",
            src.`in`().addr(), rawInput, detailed
        )

        val input = rawInput.trim()
        val normalized = if (input.startsWith("http", ignoreCase = true)) resolveB23(input) else input
        if (normalized == null) {
            src.reply("无法解析短链接：$input")
            return 0
        }

        val id = VideoId.parse(normalized)
        if (id == null) {
            src.reply("无效的视频编号（仅支持 AV/BV 或 b23 短链）")
            return 0
        }

        return try {
            val video = fetchVideo(id)
            if (video == null) {
                src.reply("未找到该视频")
                0
            } else {
                val link = "https://www.bilibili.com/video/${video.bvid}"
                val text = if (detailed) video.toDetailedText(link) else video.toSimpleText(link)
                val out = if (!video.cover.isNullOrBlank()) "$text\n${video.cover}" else text
                src.reply(out)
                1
            }
        } catch (e: Exception) {
            log.error("Exception executing BilibiliCommand for input='{}'", normalized, e)
            src.reply("获取视频信息失败：${e.message ?: "unknown error"}")
            0
        }
    }

    private sealed class VideoId {
        data class Bv(val bvid: String) : VideoId()
        data class Av(val aid: Long) : VideoId()

        companion object {
            fun parse(input: String): VideoId? {
                BV_PATTERN.find(input)?.groupValues?.getOrNull(1)?.let { return Bv("BV$it") }
                AV_PATTERN.find(input)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return Av(it) }
                return null
            }
        }
    }

    private data class BiliVideo(
        val bvid: String,
        val title: String,
        val tname: String?,
        val desc: String?,
        val cover: String?,
        val pubDateMs: Long,

        val ownerName: String?,
        val ownerMid: Long,
        val fans: Long,

        val view: Long,
        val danmaku: Long,
        val reply: Long,
        val favorite: Long,
        val coin: Long,
        val share: Long,
        val like: Long,

        val pageCount: Int
    ) {
        fun toSimpleText(link: String): String = """
            $link
            标题：$title
            类型：${tname.orEmpty().ifBlank { "未知" }}
            UP 主：${ownerName.orEmpty().ifBlank { "未知" }}
            日期：${pubDateMs.formatTime()}
            详细内容：
        """.trimIndent()

        fun toDetailedText(link: String): String {
            val sb = StringBuilder(512)
            sb.append(link).append('\n')
            sb.append("标题：").append(title)
            if (pageCount > 1) sb.append("（").append(pageCount).append("P）")
            sb.append(" | 类型：").append(tname.orEmpty().ifBlank { "未知" }).append('\n')

            sb.append("UP主：").append(ownerName.orEmpty().ifBlank { "未知" })
                .append(" | 粉丝：").append(fans.formatNum()).append('\n')

            desc?.takeIf { it.isNotBlank() }?.let {
                sb.append("简介：").append(it.truncate(160)).append('\n')
            }

            sb.append("观看：").append(view.formatNum())
                .append(" | 弹幕：").append(danmaku.formatNum())
                .append(" | 评论：").append(reply.formatNum()).append('\n')

            sb.append("喜欢：").append(like.formatNum())
                .append(" | 投币：").append(coin.formatNum())
                .append(" | 收藏：").append(favorite.formatNum())
                .append(" | 分享：").append(share.formatNum()).append('\n')

            sb.append("日期：").append(formatTime(pubDateMs))
            return sb.toString()
        }
    }

    private fun fetchVideo(id: VideoId): BiliVideo? {
        val api = when (id) {
            is VideoId.Bv -> buildViewApi(bvid = id.bvid, aid = null)
            is VideoId.Av -> buildViewApi(bvid = null, aid = id.aid)
        }

        val root = getJson(api, timeoutSec = 8) ?: return null
        val code = root.int("code")
        if (code != 0) return null

        val data = root.obj("data") ?: return null

        val bvid = data.str("bvid") ?: (id as? VideoId.Bv)?.bvid ?: return null
        val title = data.str("title").orEmpty()
        val tname = data.str("tname")
        val desc = data.str("desc")
        val cover = data.str("pic")
        val pubDateMs = publishDateMs(data)

        val owner = data.obj("owner")
        val ownerName = owner?.str("name")
        val ownerMid = owner?.long("mid") ?: 0L

        val stat = data.obj("stat")
        val view = stat?.long("view") ?: 0L
        val danmaku = stat?.long("danmaku") ?: 0L
        val reply = stat?.long("reply") ?: 0L
        val favorite = stat?.long("favorite") ?: 0L
        val coin = stat?.long("coin") ?: 0L
        val share = stat?.long("share") ?: 0L
        val like = stat?.long("like") ?: 0L

        val pageCount = pageCount(data)

        val fans = if (ownerMid > 0) fetchFans(ownerMid) ?: 0L else 0L

        return BiliVideo(
            bvid = bvid,
            title = title,
            tname = tname,
            desc = desc,
            cover = cover,
            pubDateMs = pubDateMs,
            ownerName = ownerName,
            ownerMid = ownerMid,
            fans = fans,
            view = view,
            danmaku = danmaku,
            reply = reply,
            favorite = favorite,
            coin = coin,
            share = share,
            like = like,
            pageCount = pageCount
        )
    }

    private fun fetchFans(mid: Long): Long? {
        val relApi = "https://api.bilibili.com/x/relation/stat?vmid=$mid"
        return runCatching {
            val rel = getJson(relApi, timeoutSec = 6) ?: return null
            if (rel.int("code") != 0) return null
            rel.obj("data")?.long("follower")
        }.getOrNull()
    }

    private fun resolveB23(url: String): String? {
        return try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", UA)
                .GET()
                .build()

            log.debug("Resolving b23 short URL: {}", url)
            val resp = HTTP.send(req, HttpResponse.BodyHandlers.discarding())

            val location = resp.headers().firstValue("location").orElse(null) ?: return null
            BV_PATTERN.find(location)?.groupValues?.getOrNull(1)?.let { return "BV$it" }

            val req2 = HttpRequest.newBuilder()
                .uri(URI.create(location))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", UA)
                .GET()
                .build()

            log.debug("Performing secondary request to {}", location)
            val resp2 = HTTP.send(req2, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))

            BV_PATTERN.find(resp2.uri().toString())?.groupValues?.getOrNull(1)?.let { return "BV$it" }
            BV_PATTERN.find(resp2.body())?.groupValues?.getOrNull(1)?.let { return "BV$it" }

            null
        } catch (_: HttpTimeoutException) {
            log.warn("Resolve b23 timeout: {}", url)
            null
        } catch (e: Exception) {
            log.warn("Resolve b23 failed: {}", url, e)
            null
        }
    }

    private fun getJson(url: String, timeoutSec: Int): JsonObject? {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSec.toLong()))
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .GET()
            .build()

        val resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (resp.statusCode() / 100 != 2) return null

        return runCatching {
            JsonParser.parseString(resp.body()).asJsonObject
        }.getOrNull()
    }

    private fun buildViewApi(bvid: String?, aid: Long?): String {
        return if (bvid != null) {
            "https://api.bilibili.com/x/web-interface/view?bvid=" +
                    URLEncoder.encode(bvid, StandardCharsets.UTF_8)
        } else {
            "https://api.bilibili.com/x/web-interface/view?aid=$aid"
        }
    }

    private fun publishDateMs(data: JsonObject): Long {
        val sec = data.long("pubdate")
        return if (sec <= 0) 0L else sec * 1000L
    }

    private fun pageCount(data: JsonObject?): Int {
        val byArray = if (data != null && data.has("pages") && data["pages"].isJsonArray) {
            data.getAsJsonArray("pages").size()
        } else 0
        val byField = data?.int("videos") ?: 0
        return maxOf(1, maxOf(byArray, byField))
    }
}
