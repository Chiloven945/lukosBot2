package top.chiloven.lukosbot2.commands.impl.cave

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.auth.AuthorizationService
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal
import top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument

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

    override fun name(): String = "cave"

    override fun aliases(): List<String> = listOf("c")

    override fun description(): String = "回声洞：保存并重新发送文本或图片条目"

    override fun usage(): UsageNode =
        UsageNode.root(name())
            .description(description())
            .alias(aliases())
            .syntax("随机发送一个条目")
            .syntax("发送指定编号条目", UsageNode.arg("number"))
            .syntax("添加当前消息或被引用消息为条目", UsageNode.arg("add"))
            .syntax("直接添加一段文本为条目", UsageNode.arg("add"), UsageNode.arg("message"))
            .syntax("删除指定编号条目", UsageNode.arg("delete"), UsageNode.arg("number"))
            .param("number", "正整数编号")
            .param("message", "要直接保存的文本内容")
            .note("add/delete 仅 bot admin 可用。编号单调递增，删除后不会回收。")
            .example(
                "cave",
                "cave 12",
                "cave add",
                "cave add 今天的洞内留言",
                "cave delete 12"
            )
            .build()

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    recallRandom(ctx.source)
                    1
                }
                .then(
                    literal("add")
                        .executes { ctx ->
                            add(ctx.source)
                            1
                        }
                        .then(
                            argument("message", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    add(ctx.source)
                                    1
                                }
                        )
                )
                .then(
                    literal("delete")
                        .then(
                            argument("number", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    delete(ctx.source, IntegerArgumentType.getInteger(ctx, "number"))
                                    1
                                }
                        )
                )
                .then(
                    argument("number", IntegerArgumentType.integer(1))
                        .executes { ctx ->
                            recallByNo(ctx.source, IntegerArgumentType.getInteger(ctx, "number"))
                            1
                        }
                )
        )
    }

    private fun recallRandom(src: CommandSource) {
        val entry = caveService.random()
        if (entry == null) {
            src.reply("还没有回声洞条目。")
            return
        }
        src.reply(caveService.toOutbound(src, entry))
    }

    private fun recallByNo(src: CommandSource, no: Int) {
        val entry = caveService.get(no)
        if (entry == null) {
            src.reply("编号 #$no 不存在或已被删除。")
            return
        }
        src.reply(caveService.toOutbound(src, entry))
    }

    private fun add(src: CommandSource) {
        if (!authz.ensureBotAdmin(src, "添加 cave 条目")) {
            return
        }
        try {
            val entry = caveService.add(src)
            src.reply("已添加回声洞条目 #${entry.no}")
        } catch (e: Exception) {
            src.reply("添加失败：${e.message ?: e::class.java.simpleName}")
        }
    }

    private fun delete(src: CommandSource, no: Int) {
        if (!authz.ensureBotAdmin(src, "删除 cave 条目")) {
            return
        }
        val deleted = caveService.delete(no)
        if (!deleted) {
            src.reply("编号 #$no 不存在或已被删除。")
            return
        }
        src.reply("已删除回声洞条目 #$no")
    }

}
