package top.chiloven.lukosbot2.commands.impl

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.definition.ArgType
import top.chiloven.lukosbot2.commands.definition.ValueValidator
import top.chiloven.lukosbot2.commands.definition.dsl.arg
import top.chiloven.lukosbot2.commands.definition.dsl.botCommand
import top.chiloven.lukosbot2.commands.definition.dsl.opt
import top.chiloven.lukosbot2.util.MathUtils

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["coin"],
    havingValue = "true",
    matchIfMissing = true
)
class CoinCommand : IBotCommand {

    override fun definition() = botCommand("coin") {
        description = "抛硬币"
        argv {
            positional("count", ArgType.LongType) {
                required = false
                default = 1L
                description = "硬币数量"
                validator = ValueValidator { v ->
                    val n = v as? Long ?: return@ValueValidator "硬币数量必须是正整数。"
                    if (n <= 0L) "硬币数量必须是正整数。" else null
                }
            }
            execute { args -> source.reply(runCoin(args.get<Long>("count"))) }
        }
        syntax("抛硬币（默认 1 个）", opt(arg("count")))
        param("count", "硬币数量（正整数）")
        example("coin 10")
    }

    private fun runCoin(times: Long): String {
        if (times <= 0) return "硬币数量必须是正整数。"
        return try {
            val r = MathUtils.approximateMultinomial(times, 0.499999999999, 0.499999999999, 0.000000000002)
            if (times == 1L) {
                "你抛了 1 个硬币。\n" + when {
                    r[0] == 1L -> "是正面。"
                    r[1] == 1L -> "是反面。"
                    else -> "它立起来了！"
                }
            } else {
                """
                你抛了 %d 个硬币。
                其中 %d 个是正面，%d 个是反面……
                还有 %d 个立起来了！
                """.trimIndent().format(times, r[0], r[1], r[2])
            }
        } catch (e: IllegalArgumentException) {
            "抛硬币失败：${e.message}"
        }
    }

}
