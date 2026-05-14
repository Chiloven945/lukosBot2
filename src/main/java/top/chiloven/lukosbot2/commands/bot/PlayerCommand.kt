package top.chiloven.lukosbot2.commands.bot

import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.arg
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.core.command.definition.dsl.lit
import top.chiloven.lukosbot2.core.command.definition.dsl.oneOf
import top.chiloven.lukosbot2.util.feature.MojangApi
import java.io.IOException

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["player"],
    havingValue = "true",
    matchIfMissing = true
)
class PlayerCommand : IBotCommand {

    private val log = LogManager.getLogger(PlayerCommand::class.java)

    override fun definition() = botCommand("player") {
        description = "查询 Java 版玩家信息"

        argv {
            positional("data", ArgType.StringType) {
                required = true
                description = "玩家用户名或 UUID"
            }
            positional("mode", ArgType.StringType) {
                required = false
                description = "-u 或 -n"
            }
            execute { args ->
                val data = args.get<String>("data")
                val mode = args.getOrNull<String>("mode") ?: ""

                try {
                    val result: String = when (mode) {
                        "-u" -> MojangApi.getUuidFromName(data) ?: "未找到用户"
                        "-n" -> MojangApi.getNameFromUuid(data) ?: "未找到 UUID"
                        else -> MojangApi.getMcPlayerInfo(data).toString()
                    }

                    source.reply(result)
                } catch (e: IOException) {
                    log.warn("Failed to fetch player info: {}", e.message, e)
                    source.reply("获取玩家信息失败，请检查输入后重试。")
                }
            }
        }

        syntax(
            "查询玩家信息",
            oneOf(
                arg("name"),
                arg("uuid")
            )
        )
        syntax(
            "根据用户名获取 UUID",
            arg("name"),
            lit("-u")
        )
        syntax(
            "根据 UUID 获取用户名",
            arg("uuid"),
            lit("-n")
        )
        param("name", "玩家用户名（Java 版）")
        param("uuid", "玩家 UUID（不带横线或带横线均可）")
        optionDoc("-u", "强制按\"用户名 -> UUID\"查询")
        optionDoc("-n", "强制按\"UUID -> 用户名\"查询")

        example(
            "player jeb_",
            "player Notch -u"
        )
    }

}
