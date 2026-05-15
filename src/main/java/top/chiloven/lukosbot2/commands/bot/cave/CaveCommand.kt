package top.chiloven.lukosbot2.commands.bot.cave

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.auth.AuthorizationService
import top.chiloven.lukosbot2.core.command.bot.CommandSource
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.arg
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["cave"],
    havingValue = "true",
    matchIfMissing = true
)
class CaveCommand(
    private val caveService: CaveService,
    private val authz: AuthorizationService
) : IBotCommand {

    private val commandDefinition = botCommand("cave") {
        alias("c")
        description = "回声洞：保存并随机发送文本或图片"

        literal("add") {
            description = "添加条目"
            raw("message", required = false) {
                add(source)
            }
            example("cave add", "cave add message")
        }

        literal("delete") {
            description = "删除指定编号条目"
            argv {
                positional("number", ArgType.IntType) {
                    required = true
                    description = "正整数编号"
                }
                execute { args ->
                    delete(source, args.get("number"))
                }
            }
            example("cave delete 12")
        }

        argv {
            positional("number", ArgType.IntType) {
                required = false
                default = 0
                description = "正整数编号"
            }
            execute { args ->
                val no = args.get<Int>("number")
                if (no <= 0) recallRandom(source)
                else recallByNo(source, no)
            }
        }

        syntax("随机发送一个条目")
        syntax("发送指定编号条目", arg("number"))

        example(
            "cave",
            "cave 12"
        )
        note("add/delete 仅机器人管理员可用。")
    }

    override fun definition() = commandDefinition

    private fun recallRandom(src: CommandSource) {
        val entry = caveService.random() ?: run {
            src.reply("还没有任何回声洞条目。")
            return
        }
        src.reply(caveService.toOutbound(src, entry, includeMeta = true))
    }

    private fun recallByNo(src: CommandSource, no: Int) {
        val entry = caveService.get(no) ?: run {
            src.reply("编号 #$no 不存在。")
            return
        }
        src.reply(caveService.toOutbound(src, entry, includeMeta = true))
    }

    private fun add(src: CommandSource) {
        if (!authz.ensureBotAdmin(src, "添加回声洞条目")) return
        try {
            src.reply("已添加回声洞条目 #${caveService.add(src).no}。")
        } catch (e: Exception) {
            src.reply("添加失败：${e.message ?: "请稍后再试。"}")
        }
    }

    private fun delete(src: CommandSource, no: Int) {
        if (!authz.ensureBotAdmin(src, "删除回声洞条目")) return

        if (!caveService.delete(no)) {
            src.reply("编号 #$no 不存在。")
            return
        }

        src.reply("已删除回声洞条目 #$no。")
    }

}
