package top.chiloven.lukosbot2.commands.impl.motd

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.model.message.media.BytesRef
import top.chiloven.lukosbot2.model.message.outbound.OutImage
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

    override fun description(): String = "查询 Minecraft Java 版服务器状态"

    override fun usage(): UsageNode =
        UsageNode.root(name())
            .description(description())
            .syntax("自动选择查询方式", UsageNode.arg("address[:port]"))
            .subcommand("api", "强制使用 mcsrvstat.us API 查询") { b ->
                b.syntax("强制使用 API 查询", UsageNode.arg("address[:port]"))
                    .param("address[:port]", "服务器地址（支持 SRV 域名、IPv4 / IPv6，可选端口，默认 25565）")
                    .example("motd api play.example.com")
            }
            .subcommand("direct", "强制使用直连协议查询") { b ->
                b.syntax("强制使用直连协议查询", UsageNode.arg("address[:port]"))
                    .param("address[:port]", "服务器地址（支持 SRV 域名、IPv4 / IPv6，可选端口，默认 25565）")
                    .example("motd direct play.example.com", "motd self play.example.com")
            }
            .param("address[:port]", "服务器地址（支持 SRV 域名、IPv4 / IPv6，可选端口，默认 25565）")
            .example(
                "motd play.example.com",
                "motd api play.example.com:25565",
                "motd direct [2001:db8::1]:25565"
            )
            .note(
                "不指定方式时会自动查询：优先使用 mcsrvstat.us，失败后回退到直连协议。",
                "可显式指定 api / direct / self / auto。",
                "未显式指定端口时，直连链路会额外尝试解析 _minecraft._tcp SRV 记录。"
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
                    literal("api")
                        .then(
                            argument("address", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    executeQuery(
                                        src = ctx.source,
                                        address = StringArgumentType.getString(ctx, "address"),
                                        mode = MotdQueryService.QueryMode.API,
                                    )
                                }
                        )
                )
                .then(
                    literal("direct")
                        .then(
                            argument("address", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    executeQuery(
                                        src = ctx.source,
                                        address = StringArgumentType.getString(ctx, "address"),
                                        mode = MotdQueryService.QueryMode.DIRECT,
                                    )
                                }
                        )
                )
                .then(
                    literal("self")
                        .then(
                            argument("address", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    executeQuery(
                                        src = ctx.source,
                                        address = StringArgumentType.getString(ctx, "address"),
                                        mode = MotdQueryService.QueryMode.DIRECT,
                                    )
                                }
                        )
                )
                .then(
                    literal("auto")
                        .then(
                            argument("address", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    executeQuery(
                                        src = ctx.source,
                                        address = StringArgumentType.getString(ctx, "address"),
                                        mode = MotdQueryService.QueryMode.AUTO,
                                    )
                                }
                        )
                )
                .then(
                    argument("address", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val rawInput = StringArgumentType.getString(ctx, "address")
                            val parsedMode = rawInput.substringBefore(' ', missingDelimiterValue = rawInput)
                                .let(MotdQueryService.QueryMode::parse)

                            if (parsedMode != null && rawInput.contains(' ')) {
                                val address = rawInput.substringAfter(' ').trim()
                                executeQuery(ctx.source, address, parsedMode)
                            } else {
                                executeQuery(ctx.source, rawInput, MotdQueryService.QueryMode.AUTO)
                            }
                        }
                )
        )
    }

    private fun executeQuery(
        src: CommandSource,
        address: String,
        mode: MotdQueryService.QueryMode,
    ): Int {
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
                                "image/png",
                            )
                        )
                    )
                )
            } else {
                src.reply(text)
            }
            1
        } catch (e: IllegalArgumentException) {
            src.reply(e.message ?: "地址格式不正确")
            0
        } catch (e: Exception) {
            log.warn("Unable to get MOTD for address: {}, mode: {}", address, mode, e)
            src.reply("查询服务器状态失败：${e.message ?: "请稍后再试。"}")
            0
        }
    }


}
