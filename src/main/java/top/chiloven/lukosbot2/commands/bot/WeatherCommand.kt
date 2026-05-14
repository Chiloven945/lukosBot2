package top.chiloven.lukosbot2.commands.bot

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["weather"],
    havingValue = "true",
    matchIfMissing = true
)
class WeatherCommand : IBotCommand {

    override fun definition() = botCommand("weather") {
        description = "查询天气（暂未开放）"
        syntax("该命令暂未开放")
        note("天气查询功能尚未接入，暂时无法使用。")
        execute {
            source.reply("天气查询功能尚未接入，请关注后续更新。")
        }
    }

}
