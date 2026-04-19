package top.chiloven.lukosbot2.commands.impl.motd

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal
import top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument

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

    override fun name(): String = "motd"

    override fun description(): String = "获取 Minecraft Java 版服务器 MOTD 信息"

    override fun usage(): UsageNode =
        UsageNode.root(name())
            .description(description())
            .syntax("查询 Minecraft Java 版服务器 MOTD", UsageNode.arg("address[:port]"))
            .param("address[:port]", "服务器地址（支持 SRV 域名、IPv4 / IPv6，可选端口，默认 25565）")
            .example(
                "motd play.example.com",
                "motd play.example.com:25565",
                "motd [2001:db8::1]:25565"
            )
            .note(
                "优先通过 mcsrvstat.us 查询；若接口不可用，会自动回退到机器人自身的 Java 状态请求。",
                "未显式指定端口时，回退链路会额外尝试解析 _minecraft._tcp SRV 记录。"
            )
            .build()

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    sendUsage(ctx.source)
                    1
                }
                .then(
                    argument("address", StringArgumentType.greedyString())
                        .executes { ctx ->
                            executeQuery(ctx.source, StringArgumentType.getString(ctx, "address"))
                        }
                )
        )
    }

    private fun executeQuery(src: CommandSource, address: String): Int {
        return try {
            val data = motdQueryService.query(address)
            src.reply(data.formatted())

            data.faviconBytes()?.takeIf { it.isNotEmpty() }?.let { faviconBytes ->
                src.reply(OutboundMessage.imageBytesPng(src.addr(), faviconBytes, "favicon.png"))
            }
            1
        } catch (e: IllegalArgumentException) {
            src.reply(e.message ?: "地址格式不正确")
            0
        } catch (e: Exception) {
            log.warn("Unable to get MOTD for address: {}", address, e)
            src.reply("获取 MOTD 失败：${e.message ?: "未知错误"}")
            0
        }
    }

}
