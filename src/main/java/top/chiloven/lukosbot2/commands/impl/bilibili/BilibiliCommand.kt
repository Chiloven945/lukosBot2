package top.chiloven.lukosbot2.commands.impl.bilibili

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal
import top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["bilibili"],
    havingValue = "true",
    matchIfMissing = true,
)
class BilibiliCommand(
    private val bilibiliQueryService: BilibiliQueryService,
) : IBotCommand {

    private val log = LogManager.getLogger(BilibiliCommand::class.java)

    override fun name(): String = "bilibili"

    override fun aliases(): List<String> = listOf("bili")

    override fun description(): String = "查询 Bilibili 视频信息"

    override fun usage(): UsageNode {
        val target = UsageNode.oneOf(UsageNode.arg("code"), UsageNode.arg("link"))
        return UsageNode.root(name())
            .description(description())
            .alias(aliases())
            .syntax("查询 Bilibili 视频信息", target, UsageNode.opt(UsageNode.lit("-i")))
            .param("code", "视频编号（AV/BV）")
            .param("link", "视频链接或 b23 短链")
            .option("-i", "输出更多信息")
            .example(
                "bilibili BV1GJ411x7h7",
                "bilibili av170001",
                "bilibili https://b23.tv/BV1GJ411x7h7 -i",
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
                    argument("input", StringArgumentType.greedyString())
                        .executes { ctx ->
                            handleInput(ctx.source, StringArgumentType.getString(ctx, "input"))
                        },
                ),
        )
    }

    private fun handleInput(src: CommandSource, rawInput: String): Int {
        val parsed = ParsedInput.parse(rawInput)
        if (parsed == null) {
            sendUsage(src)
            return 0
        }

        log.info("BilibiliCommand invoked by {} with input='{}', detailed={}", src.addr(), parsed.target, parsed.detailed,)

        return runCatching {
            execute(src, parsed)
        }.getOrElse { e ->
            log.warn("Bilibili command failed for input='{}': {}", parsed.target, e.message, e)
            src.reply(friendlyError(e))
            0
        }
    }

    private fun execute(src: CommandSource, parsed: ParsedInput): Int {
        val video = bilibiliQueryService.query(parsed.target)
        if (video == null) {
            src.reply("未找到该视频，或输入不是有效的 AV/BV 编号、B站链接、b23 短链。")
            return 0
        }

        val text = video.toReplyText(detailed = parsed.detailed)
        val cover = video.cover?.takeIf { it.isNotBlank() }
        src.reply(cover?.let { "$text\n$it" } ?: text)
        return 1
    }

    private fun friendlyError(error: Throwable): String {
        val msg = error.message?.trim().orEmpty()
        return when {
            msg.contains("timeout", ignoreCase = true) -> "请求 Bilibili 接口超时，请稍后再试。"
            msg.startsWith("HTTP 404") -> "未找到该视频。"
            msg.isNotEmpty() -> "获取视频信息失败：$msg"
            else -> "获取视频信息失败。"
        }
    }

    private data class ParsedInput(
        val target: String,
        val detailed: Boolean,
    ) {

        companion object {

            fun parse(raw: String): ParsedInput? {
                val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (tokens.isEmpty()) return null

                val detailed = tokens.any { it.equals("-i", ignoreCase = true) }
                val target = tokens.firstOrNull { !it.equals("-i", ignoreCase = true) } ?: return null
                return ParsedInput(target = target, detailed = detailed)
            }

        }

    }

}
