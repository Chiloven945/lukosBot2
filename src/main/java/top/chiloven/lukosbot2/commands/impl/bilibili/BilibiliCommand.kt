package top.chiloven.lukosbot2.commands.impl.bilibili

import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.definition.ArgType
import top.chiloven.lukosbot2.commands.definition.dsl.*

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

    private val commandDefinition = botCommand("bilibili") {
        alias("bili")
        description = "查询 Bilibili 视频信息"

        argv {
            positional("target", ArgType.StringType) {
                required = true
                description = "视频编号（AV / BV）或链接"
            }
            option("detailed") {
                names = listOf("-i")
                type = ArgType.BooleanType
                default = false
                description = "展示更完整的视频信息"
            }
            execute { args ->
                execute(source, args.get("target"), args.get<Boolean>("detailed"))
            }
        }

        syntax("查询 Bilibili 视频信息", oneOf(arg("code"), arg("link")), opt(lit("-i")))
        param("code", "视频编号（AV / BV）")
        param("link", "视频链接或 b23.tv 短链")
        optionDoc("-i", "展示更完整的视频信息")
        example("bilibili BV1GJ411x7h7", "bilibili av170001", "bilibili https://b23.tv/BV1GJ411x7h7 -i")
    }

    override fun definition() = commandDefinition

    private fun execute(
        src: top.chiloven.lukosbot2.core.command.CommandSource,
        target: String,
        detailed: Boolean
    ): Int {
        log.info("BilibiliCommand invoked with target='{}', detailed={}", target, detailed)
        return runCatching {
            val video = bilibiliQueryService.query(target)
            if (video == null) {
                src.reply("未找到该视频。"); return 0
            }
            val text = video.toReplyText(detailed = detailed)
            val cover = video.cover?.takeIf { it.isNotBlank() }
            src.reply(cover?.let { "$text\n$it" } ?: text)
            1
        }.getOrElse { e ->
            log.warn("Bilibili command failed: {}", target, e)
            src.reply(friendlyError(e))
            0
        }
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

}
