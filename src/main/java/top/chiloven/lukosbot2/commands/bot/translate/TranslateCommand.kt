package top.chiloven.lukosbot2.commands.bot.translate

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.config.CommandConfigProp
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.*

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["translate"],
    havingValue = "true",
    matchIfMissing = true
)
class TranslateCommand(ccp: CommandConfigProp) : IBotCommand {

    private val translate = ccp.translate
    private val ts = TranslationService(translate)

    override fun definition() = botCommand("translate") {
        alias("tr")
        description = "翻译文本"

        argv {
            positional("text", ArgType.StringType) { required = true; greedy = true; description = "要翻译的文本" }
            option("from") { names = listOf("-f"); type = ArgType.StringType; description = "源语言" }
            option("to") { names = listOf("-t"); type = ArgType.StringType; description = "目标语言" }
            execute { args ->
                val from = args.getOrNull<String>("from") ?: "auto"
                val to = args.getOrNull<String>("to") ?: translate.defaultLang
                val text = args.get<String>("text")
                source.reply(ts.translate(from, to, text))
            }
        }

        syntax(
            "翻译文本（可选指定源/目标语言）",
            opt(group(lit("-f"), arg("from_lang"))),
            opt(group(lit("-t"), arg("to_lang"))),
            arg("text")
        )
        param("text", "要翻译的文本（支持空格）")
        optionDoc("-f", "指定源语言（可选；不指定则自动检测）")
        optionDoc("-t", "指定目标语言（可选；不指定则使用默认目标语言）")
        example(
            "/translate the quick brown fox",
            "/translate -f en -t zh-Hans the quick brown fox",
            "/translate -t ja Hello"
        )
    }

}
