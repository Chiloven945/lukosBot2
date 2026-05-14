package top.chiloven.lukosbot2.commands.bot.wikis

import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.core.command.bot.CommandSource
import top.chiloven.lukosbot2.core.command.definition.dsl.arg
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.core.command.definition.dsl.oneOf
import top.chiloven.lukosbot2.core.model.message.media.BytesRef
import top.chiloven.lukosbot2.core.model.message.outbound.OutFile
import top.chiloven.lukosbot2.core.model.message.outbound.OutImage
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.util.feature.WebScreenshot
import top.chiloven.lukosbot2.util.feature.WebToMarkdown
import java.time.Duration

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["mcwiki"],
    havingValue = "true",
    matchIfMissing = true
)
class McWikiCommand : IWikiishCommand {

    private val log = LogManager.getLogger(McWikiCommand::class.java)

    override fun defaultLang() = "zh"
    override fun pathPrefix() = "/w/"
    override fun domainRoot() = "minecraft.wiki"

    private val commandDefinition = botCommand("mcwiki") {
        description = "查询 Minecraft Wiki，支持简介、截图和导出 Markdown"

        literal("md") {
            description = "抓取整页并转为 Markdown 文件"
            raw("link") { link -> runMarkdown(source, link.trim()) }
            param("link", "Minecraft Wiki 链接")
            param("article", "词条名（可用 `en:` 前缀指定语言）")
        }

        literal("ss") {
            description = "生成页面截图"
            raw("link") { link -> runScreenshot(source, link.trim()) }
            param("link", "Minecraft Wiki 链接")
            param("article", "词条名（可用 `en:` 前缀指定语言）")
        }

        raw("link", required = false) { link ->
            if (link.isBlank()) sendUsage(source)
            else runSummary(source, link.trim())
        }

        syntax("返回标题和简介", oneOf(arg("link"), arg("article")))
        example(
            "mcwiki 僵尸猪灵",
            "mcwiki en:Zombie_Piglin",
            "mcwiki md https://zh.minecraft.wiki/w/僵尸猪灵",
            "mcwiki ss 下界合金"
        )
        note("词条名支持 `en:` 前缀指定语言，例如：`en:Zombie_Piglin`。")
    }

    override fun definition() = commandDefinition

    private fun runSummary(src: CommandSource, linkOrTitle: String) {
        try {
            val url = normalize(linkOrTitle)
            if (isNot(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。"); return
            }

            val summary = fetchTitleAndLead(url)
            if (summary.title.isBlank()) {
                src.reply("未获取到有效内容，请检查条目是否存在。"); return
            }
            src.reply(buildString {
                append(summary.title)
                if (summary.lead.isNotBlank()) append('\n').append(summary.lead)
            })
        } catch (e: Exception) {
            log.warn("McWiki summary failed: {}", linkOrTitle, e)
            src.reply("获取失败：${e.message}")
        }
    }

    private fun runMarkdown(src: CommandSource, linkOrTitle: String) {
        try {
            val url = normalize(linkOrTitle)
            if (isNot(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。"); return
            }

            val md = WebToMarkdown.fetchAndConvertWithSelectors(
                url,
                "h1#firstHeading",
                "#content, #mw-content-text",
                "mcwiki"
            )

            val ref = BytesRef(md.filename(), md.bytes(), md.mime())
            src.reply(OutboundMessage(src.addr(), listOf(OutFile(ref, "已转换为 Markdown。", md.filename(), md.mime()))))
        } catch (e: Exception) {
            log.warn("McWiki md failed: {}", linkOrTitle, e)
            src.reply("转换失败：${e.message}")
        }
    }

    private fun runScreenshot(src: CommandSource, linkOrTitle: String) {
        try {
            val url = normalize(linkOrTitle)
            if (isNot(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。"); return
            }

            val cd = WebScreenshot.screenshotMcWiki(url)
            val ref = BytesRef(cd.filename(), cd.bytes(), cd.mime())
            src.reply(OutboundMessage(src.addr(), listOf(OutImage(ref, "截图如下。", cd.filename(), cd.mime()))))
        } catch (e: Exception) {
            log.warn("McWiki screenshot failed: {}", linkOrTitle, e)
            src.reply("截图失败：${e.message}")
        }
    }

    private data class TitleLead(val title: String, val lead: String)

    private fun fetchTitleAndLead(url: String): TitleLead {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (compatible; ${Constants.UA})")
            .timeout(Duration.ofSeconds(15).toMillis().toInt())
            .get()

        val title = doc.selectFirst("h1#firstHeading").textOrEmpty()
        val container =
            doc.selectFirst("#mw-content-text .mw-parser-output") ?: doc.selectFirst("#content") ?: return TitleLead(
                title,
                ""
            )
        val lead = buildString {
            var added = 0
            for (p in container.select("> p")) {
                val t = p.text().trim()
                if (t.isEmpty()) continue
                if (isNotEmpty()) append("\n\n")
                append(t)
                added++
                if (added >= 2 || length >= 500) break
            }
        }.trim()

        return TitleLead(title, lead)
    }

    private fun Element?.textOrEmpty() =
        this?.text()?.trim().orEmpty()

}
