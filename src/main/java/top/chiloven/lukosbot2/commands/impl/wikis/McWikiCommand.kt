package top.chiloven.lukosbot2.commands.impl.wikis

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.model.Attachment
import top.chiloven.lukosbot2.model.MessageOut
import top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal
import top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument
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

    override fun defaultLang(): String = "zh"
    override fun pathPrefix(): String = "/w/"
    override fun domainRoot(): String = "minecraft.wiki"

    override fun name(): String = "mcwiki"
    override fun description(): String = "Minecraft Wiki 工具：返回标题+简介，或整页 Markdown / 截图"

    override fun usage(): UsageNode {
        val target = UsageNode.oneOf(UsageNode.arg("link"), UsageNode.arg("article"))
        return UsageNode.root(name())
            .description(description())
            .syntax("返回标题+简介", target)
            .subcommand("md", "抓取整页并转为 Markdown 文件") { b ->
                b.syntax("抓取整页并转为 Markdown 文件", target)
            }
            .subcommand("ss", "生成页面截图") { b ->
                b.syntax("生成页面截图", target)
            }
            .param("link", "Minecraft Wiki 链接")
            .param("article", "词条名（可用 `en:` 前缀指定语言）")
            .example(
                "mcwiki 僵尸猪灵",
                "mcwiki en:Zombie_Piglin",
                "mcwiki md https://zh.minecraft.wiki/w/僵尸猪灵",
                "mcwiki ss 下界合金"
            )
            .note("词条名支持 `en:` 前缀指定语言，例如：`en:Zombie_Piglin`。")
            .build()
    }

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                // /mcwiki <linkOrTitle>  -> summary
                .then(
                    argument("link", greedyString())
                        .executes { ctx ->
                            val link = StringArgumentType.getString(ctx, "link").trim()
                            runSummary(ctx.source, link)
                            1
                        }
                )
                // /mcwiki md <linkOrTitle>
                .then(
                    literal("md")
                        .then(
                            argument("link", greedyString())
                                .executes { ctx ->
                                    val link = StringArgumentType.getString(ctx, "link").trim()
                                    runMarkdown(ctx.source, link)
                                    1
                                }
                        )
                )
                // /mcwiki ss <linkOrTitle>
                .then(
                    literal("ss")
                        .then(
                            argument("link", greedyString())
                                .executes { ctx ->
                                    val link = StringArgumentType.getString(ctx, "link").trim()
                                    runScreenshot(ctx.source, link)
                                    1
                                }
                        )
                )
                .executes { ctx ->
                    sendUsage(ctx.source)
                    1
                }
        )
    }

    private fun runSummary(src: CommandSource, linkOrTitle: String) {
        try {
            val url = normalize(linkOrTitle)
            if (isNot(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。示例：/mcwiki https://zh.minecraft.wiki/w/下界合金 或 /mcwiki 下界合金")
                return
            }

            val summary = fetchTitleAndLead(url)
            if (summary.title.isBlank()) {
                src.reply("未抓到有效内容。请检查条目是否存在。")
                return
            }

            val text = buildString {
                append(summary.title)
                if (summary.lead.isNotBlank()) {
                    append('\n').append(summary.lead)
                }
            }

            src.reply(MessageOut.text(src.`in`().addr(), text))
        } catch (e: Exception) {
            log.warn("McWiki summary failed: {}", linkOrTitle, e)
            src.reply("获取失败：${e.message}")
        }
    }

    private fun runMarkdown(src: CommandSource, linkOrTitle: String) {
        try {
            val url = normalize(linkOrTitle)
            if (isNot(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。示例：/mcwiki md https://zh.minecraft.wiki/w/下界合金 或 /mcwiki md 下界合金")
                return
            }

            val md = WebToMarkdown.fetchAndConvertWithSelectors(
                url = url,
                titleSelector = "h1#firstHeading",
                contentSelectorsCsv = "#content, #mw-content-text",
                defaultTitleBase = "mcwiki"
            )

            val out = MessageOut.text(src.`in`().addr(), "已转换为 Markdown")
                .with(Attachment.fileBytes(md.filename(), md.bytes(), md.mime()))
            src.reply(out)
        } catch (e: Exception) {
            log.warn("McWiki md failed: {}", linkOrTitle, e)
            src.reply("转换失败：${e.message}")
        }
    }

    private fun runScreenshot(src: CommandSource, linkOrTitle: String) {
        try {
            val url = normalize(linkOrTitle)
            if (isNot(url)) {
                src.reply("仅支持 Minecraft Wiki 链接或条目名。示例：/mcwiki ss https://zh.minecraft.wiki/w/下界合金 或 /mcwiki ss 下界合金")
                return
            }

            val cd = WebScreenshot.screenshotMcWiki(url)
            val out = MessageOut.text(src.`in`().addr(), "截图如下：")
                .with(Attachment.imageBytes(cd.filename(), cd.bytes(), cd.mime()))
            src.reply(out)
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

        val container = doc.selectFirst("#mw-content-text .mw-parser-output")
            ?: doc.selectFirst("#content")
            ?: return TitleLead(title, "")

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

    private fun Element?.textOrEmpty(): String = this?.text()?.trim().orEmpty()
}
