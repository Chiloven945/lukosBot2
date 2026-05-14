package top.chiloven.lukosbot2.commands.bot.motd

import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.bot.CommandSource
import top.chiloven.lukosbot2.core.command.definition.dsl.arg
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.core.model.message.media.BytesRef
import top.chiloven.lukosbot2.core.model.message.outbound.OutImage
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["motd"],
    havingValue = "true",
    matchIfMissing = true
)
class MotdCommand(
    private val motdQueryService: MotdQueryService,
) : IBotCommand {

    private val log = LogManager.getLogger(MotdCommand::class.java)

    override fun definition() = botCommand("motd") {
        description = "查询 Minecraft Java 版服务器状态"

        literal("api") {
            description = "强制使用 mcsrvstat.us API 查询"
            raw("address") { address -> executeQuery(source, address, MotdQueryService.QueryMode.API) }
            param("address[:port]", "服务器地址")
        }

        literal("direct") {
            description = "强制使用直连协议查询"
            raw("address") { address -> executeQuery(source, address, MotdQueryService.QueryMode.DIRECT) }
            param("address[:port]", "服务器地址")
        }

        literal("self") {
            description = "强制使用直连协议查询"
            raw("address") { address -> executeQuery(source, address, MotdQueryService.QueryMode.DIRECT) }
            param("address[:port]", "服务器地址")
        }

        literal("auto") {
            description = "自动选择查询方式"
            raw("address") { address -> executeQuery(source, address, MotdQueryService.QueryMode.AUTO) }
            param("address[:port]", "服务器地址")
        }

        raw("address", required = false) { address ->
            if (address.isBlank()) sendUsage(source)
            else executeQuery(source, address, MotdQueryService.QueryMode.AUTO)
        }

        syntax("自动选择查询方式", arg("address[:port]"))
        param("address[:port]", "服务器地址（支持 SRV 域名、IPv4 / IPv6，可选端口，默认 25565）")
        example("motd play.example.com", "motd api play.example.com:25565", "motd direct [2001:db8::1]:25565")
        note("不指定方式时会自动查询：优先使用 mcsrvstat.us，失败后回退到直连协议。")
    }

    private fun executeQuery(src: CommandSource, address: String, mode: MotdQueryService.QueryMode): Int {
        return try {
            require(address.isNotBlank()) { "请提供服务器地址" }
            val data = motdQueryService.query(address, mode)
            val text = data.formatted()
            val faviconBytes = data.faviconBytes()
            if (faviconBytes != null && faviconBytes.isNotEmpty()) {
                src.reply(
                    OutboundMessage(
                        src.addr(),
                        listOf(
                            OutImage(
                                BytesRef("favicon.png", faviconBytes, "image/png"),
                                text,
                                "favicon.png",
                                "image/png"
                            )
                        )
                    )
                )
            } else src.reply(text)
            1
        } catch (e: IllegalArgumentException) {
            src.reply(e.message ?: "地址格式不正确"); 0
        } catch (e: Exception) {
            log.warn("MOTD failed: {}", address, e); src.reply("查询失败：${e.message}"); 0
        }
    }

}
