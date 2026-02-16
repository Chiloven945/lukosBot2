package top.chiloven.lukosbot2.commands.impl.wikis

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.model.Attachment
import top.chiloven.lukosbot2.model.MessageOut
import top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal
import top.chiloven.lukosbot2.util.brigadier.builder.RequiredArgumentBuilder.argument
import top.chiloven.lukosbot2.util.feature.WebScreenshot
import top.chiloven.lukosbot2.util.feature.WebToMarkdown

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["wiki"],
    havingValue = "true",
    matchIfMissing = true
)
class WikiCommand : IWikiishCommand {

    private val log = LogManager.getLogger(WikiCommand::class.java)

    override fun defaultLang(): String = "zh"
    override fun pathPrefix(): String = "/wiki/"
    override fun domainRoot(): String = "wikipedia.org"

    override fun name(): String = "wiki"
    override fun description(): String = "维基百科工具，支持截图和转 Markdown"

    override fun usage(): UsageNode {
        val target = UsageNode.oneOf(UsageNode.arg("link"), UsageNode.arg("article"))
        return UsageNode.root(name())
            .description(description())
            .syntax("生成页面截图", target)
            .subcommand("md", "抓取整页并转为 Markdown 文件") { b ->
                b.syntax("抓取整页并转为 Markdown 文件", target)
            }
            .param("link", "维基百科链接")
            .param("article", "词条名（可用 `en:` 前缀指定语言）")
            .example(
                "wiki Java",
                "wiki en:Albert_Einstein",
                "wiki md https://zh.wikipedia.org/wiki/Java"
            )
            .note("词条名支持 `en:` 前缀指定语言，例如：`en:Albert_Einstein`。")
            .build()
    }

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .then(
                    argument("link", greedyString())
                        .executes { ctx ->
                            val link = StringArgumentType.getString(ctx, "link").trim()
                            runScreenshot(ctx.source, link)
                            1
                        }
                )
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
                .executes { ctx ->
                    sendUsage(ctx.source)
                    1
                }
        )
    }

    private fun runScreenshot(src: CommandSource, linkOrTitle: String) {
        try {
            val url = normalize(linkOrTitle)
            if (isNot(url)) {
                src.reply("仅支持 Wikipedia 链接或词条名。示例：/wiki https://en.wikipedia.org/wiki/Java 或 /wiki en:Java")
                return
            }
            val img = WebScreenshot.screenshotWikipedia(url)
            val out = MessageOut.text(src.`in`().addr(), null)
                .with(Attachment.imageBytes(img.filename(), img.bytes(), img.mime()))
            src.reply(out)
        } catch (e: Exception) {
            log.warn("Wiki screenshot failed: {}", linkOrTitle, e)
            src.reply("截图失败：${e.message}")
        }
    }

    private fun runMarkdown(src: CommandSource, linkOrTitle: String) {
        try {
            val url = normalize(linkOrTitle)
            if (isNot(url)) {
                src.reply("仅支持 Wikipedia 链接或词条名。示例：/wiki md https://zh.wikipedia.org/wiki/Java 或 /wiki md Java")
                return
            }
            val md = WebToMarkdown.fetchWikipediaMarkdown(url)
            val out = MessageOut.text(src.`in`().addr(), "已转换为 Markdown")
                .with(Attachment.fileBytes(md.filename(), md.bytes(), md.mime()))
            src.reply(out)
        } catch (e: Exception) {
            log.warn("Wiki md failed: {}", linkOrTitle, e)
            src.reply("转换失败：${e.message}")
        }
    }
}
