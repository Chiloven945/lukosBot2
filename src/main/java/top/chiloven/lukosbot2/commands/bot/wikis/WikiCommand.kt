package top.chiloven.lukosbot2.commands.bot.wikis

import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
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

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["wiki"],
    havingValue = "true",
    matchIfMissing = true
)
class WikiCommand : IBotCommand, IWikiishCommand {

    private val log = LogManager.getLogger(WikiCommand::class.java)

    override fun defaultLang() = "zh"
    override fun pathPrefix() = "/wiki/"
    override fun domainRoot() = "wikipedia.org"

    override fun definition() = botCommand("wiki") {
        description = "查询维基百科，支持截图和导出 Markdown"

        literal("md") {
            description = "抓取整页并转为 Markdown 文件"
            raw("link") { link ->
                runMarkdown(source, link.trim())
            }
            param("link", "维基百科链接")
            param("article", "词条名（可用 `en:` 前缀指定语言）")
        }

        raw("link", required = false) { link ->
            if (link.isBlank()) sendUsage(source)
            else runScreenshot(source, link.trim())
        }

        syntax(
            "生成页面截图",
            oneOf(
                arg("link"),
                arg("article")
            )
        )

        example(
            "wiki Java",
            "wiki en:Albert_Einstein",
            "wiki md https://zh.wikipedia.org/wiki/Java"
        )
        note("词条名支持 `en:` 前缀指定语言，例如：`en:Albert_Einstein`。")
    }

    private fun runScreenshot(src: CommandSource, linkOrTitle: String) {
        try {
            val url = normalize(linkOrTitle)
            if (isNot(url)) {
                src.reply("仅支持 Wikipedia 链接或词条名。")
                return
            }

            val img = WebScreenshot.screenshotWikipedia(url)
            val ref = BytesRef(img.filename(), img.bytes(), img.mime())
            src.reply(OutboundMessage(src.addr(), listOf(OutImage(ref, null, img.filename(), img.mime()))))
        } catch (e: Exception) {
            log.warn("Wiki screenshot failed: {}", linkOrTitle, e)
            src.reply("截图失败：${e.message}")
        }
    }

    private fun runMarkdown(src: CommandSource, linkOrTitle: String) {
        try {
            val url = normalize(linkOrTitle)
            if (isNot(url)) {
                src.reply("仅支持 Wikipedia 链接或词条名。")
                return
            }

            val md = WebToMarkdown.fetchWikipediaMarkdown(url)
            val ref = BytesRef(md.filename(), md.bytes(), md.mime())
            src.reply(
                OutboundMessage(
                    src.addr(),
                    listOf(
                        OutFile(
                            ref,
                            "已转换为 Markdown。",
                            md.filename(),
                            md.mime()
                        )
                    )
                )
            )
        } catch (e: Exception) {
            log.warn("Wiki md failed: {}", linkOrTitle, e)
            src.reply("转换失败：${e.message}")
        }
    }

}
