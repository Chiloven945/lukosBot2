package top.chiloven.lukosbot2.commands.bot

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.ValueValidator
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.util.MathUtils
import java.math.BigInteger
import java.util.stream.IntStream

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["dice"],
    havingValue = "true",
    matchIfMissing = true
)
class DiceCommand : IBotCommand {

    override fun definition() = botCommand("dice") {
        description = "掷骰子，可指定骰子数量"

        argv {
            positional("count", ArgType.LongType) {
                required = false
                default = 1L
                description = "骰子数量"
                validator = ValueValidator { value ->
                    val v = value as? Long ?: return@ValueValidator "骰子数量必须是正整数。"
                    if (v <= 0L) "骰子数量必须是正整数。" else null
                }
            }

            execute { args ->
                val count = args.get<Long>("count")
                source.reply(runDice(count))
            }
        }

        example(
            "dice",
            "dice 3"
        )
    }

    private fun runDice(count: Long): String {
        if (count <= 0) return "骰子数量必须是正整数。"
        val faces = MathUtils.approximateMultinomial(count, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        if (count == 1L) {
            val face = IntStream.range(0, 6).filter { i -> faces[i] > 0 }.findFirst().orElse(0) + 1
            return """
                你掷了 1 个骰子。
                朝上的一面是……%d！
            """.trimIndent().format(face)
        } else {
            val sum = IntStream.range(0, faces.size)
                .mapToObj { i -> BigInteger.valueOf(faces[i]).multiply(BigInteger.valueOf(i + 1L)) }
                .reduce(BigInteger.ZERO) { a, b -> a.add(b) }

            return """
                你掷了 %d 个骰子。
                其中，点数为 1 的有 %d 个，2 的有 %d 个，3 的有 %d 个，4 的有 %d 个，5 的有 %d 个，6 的有 %d 个。
                它们的点数合计为 %s！
            """.trimIndent().format(count, faces[0], faces[1], faces[2], faces[3], faces[4], faces[5], sum.toString())
        }
    }

}
