package top.chiloven.lukosbot2.commands.impl

import com.mojang.brigadier.CommandDispatcher
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.MathUtils
import top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal
import java.time.LocalDate
import java.time.MonthDay

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["luck"],
    havingValue = "true",
    matchIfMissing = true
)
class LuckCommand : IBotCommand {

    private val log = LogManager.getLogger(LuckCommand::class.java)

    override fun name(): String = "luck"

    override fun aliases(): List<String> = listOf("l", "jrrp")

    override fun description(): String = "获取当日的幸运值。"

    override fun usage(): UsageNode = UsageNode.root(name())
        .description(description())
        .alias(aliases())
        .syntax("获取今日幸运值")
        .example("luck")
        .build()

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name()).executes { ctx ->
                try {
                    val today = LocalDate.now()
                    val monthDay = MonthDay.from(today)
                    val userId = ctx.source.userId()

                    val luck = MathUtils.stableRandom(
                        0, 100,
                        userId,
                        today
                    )
                    val response = generateLuckMessage(monthDay, luck)

                    ctx.source.reply("你今天的幸运值是……\n$response")
                    1
                } catch (e: Exception) {
                    log.error("Error executing luck command", e)
                    ctx.source.reply("出现未知错误，请联系开发者")
                    0
                }
            }
        )
    }

    private fun generateLuckMessage(date: MonthDay, luck: Int): String = when (date) {
        MonthDay.of(1, 1) -> "100！新年快乐！"
        MonthDay.of(2, 29) -> "$luck！是闰日诶！"
        MonthDay.of(5, 1) -> "51！五一假期快乐！"
        MonthDay.of(10, 1) -> "100！国庆假期快乐！"
        MonthDay.of(10, 31) -> "100！万圣节快乐！"
        MonthDay.of(12, 25) -> "100！圣诞节快乐！"
        else -> null
    } ?: when (luck) {
        0 -> "嗯……呃……0……？"
        18 -> "18！好耶满十八了！"
        4, 44 -> "$luck……不死不死！"
        27 -> "27！爱吃什么？"
        50 -> "50！是一半！"
        55 -> "55！呜呜！"
        60 -> "60！好耶及格了！"
        66 -> "66！666 好耶！"
        67 -> "sixty seven。嗯。"
        69 -> "69？哇噻居然是 69！"
        78 -> "咦惹，是 78……"
        88 -> "88！诶！（被打）"
        91 -> "91！恭喜！嗯。是的。"
        100 -> "登登登！100！大满贯，大成功！"

        in 1..10 -> "$luck……？仅供娱乐哦……至少比 ${luck - 1} 好！"
        in 11..20 -> "$luck……？仅供娱乐哦！"
        in 21..49 -> "$luck？没关系捏，说不好明天更幸运！"
        in 50..75 -> "$luck！好耶，出门捡到一块钱！"
        in 76..89 -> "$luck！抽奖不做分母了！"
        in 90..95 -> "$luck！！哇噻，这就是传说中的欧皇吗？"
        in 96..99 -> "居然是 $luck！！！太棒了！"

        else -> {
            log.error("Unexpected luck value: $luck")
            "$luck？欸？不应该出现这个分数的，可能是出问题了捏……"
        }
    }

}
