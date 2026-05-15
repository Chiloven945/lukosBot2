package top.chiloven.lukosbot2.commands.bot

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.definition.dsl.arg
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["echo"],
    havingValue = "true",
    matchIfMissing = true
)
class EchoCommand : IBotCommand {

    private val commandDefinition = botCommand("echo") {
        description = "原样返回文本"

        raw("text", required = false) { text ->
            if (text.isBlank()) source.reply("请输入要回显的文本。用法：/echo <text>")
            else source.reply(text)
        }

        syntax("回显输入的文本", arg("text"))
        param("text", "要回显的文本（支持空格）")

        example("echo hello")
    }

    override fun definition() = commandDefinition

}
