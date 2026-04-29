package top.chiloven.lukosbot2.commands.impl.ip

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.commands.impl.ip.IpQueryResult.IpQueryException
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal
import top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["ip"],
    havingValue = "true",
    matchIfMissing = true
)
class IpCommand(
    private val ipQueryService: IpQueryService
) : IBotCommand {

    private val log = LogManager.getLogger(IpCommand::class.java)

    override fun name(): String = "ip"

    override fun description(): String = "查询 IP 信息"

    override fun usage(): UsageNode {
        val providerOpt = UsageNode.concat(
            UsageNode.lit("--provider="),
            UsageNode.arg("providers")
        )

        return UsageNode.root(name())
            .description(description())
            .syntax("按默认数据源优先级查询", UsageNode.arg("ip_address"))
            .syntax(
                "指定一个或多个数据源查询",
                providerOpt,
                UsageNode.arg("ip_address")
            )
            .param("ip_address", "IP 地址（IPv4 / IPv6）")
            .param("providers", "数据源名称，多个用逗号分隔，例如 ipquery,ipsb")
            .option(providerOpt, "指定数据源；多个数据源会按填写顺序依次尝试")
            .example(
                "ip 1.1.1.1",
                "ip 2606:4700:4700::1111",
                "ip --provider=ipquery 1.1.1.1",
                "ip --provider=ipquery,ipsb 1.1.1.1"
            )
            .build()
    }

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    sendUsage(ctx.source)
                    1
                }
                .then(
                    argument("args", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val raw = StringArgumentType.getString(ctx, "args")
                            runQuery(ctx.source, raw)
                        }
                )
        )
    }

    private fun runQuery(src: CommandSource, raw: String): Int {
        return try {
            val args = IpCommandArgs.parse(raw)
            val result = ipQueryService.query(
                ip = args.ip,
                requestedProviders = args.providers
            )
            src.reply(result.toDisplayText())
            1
        } catch (e: IllegalArgumentException) {
            src.reply(e.message ?: "参数错误，请检查 IP 地址或数据源名称")
            0
        } catch (e: IpQueryException) {
            log.warn("IP query failed. ip={}", e.ip, e)
            src.reply(e.message ?: "IP 查询失败，请稍后重试")
            0
        } catch (e: Exception) {
            log.warn("IP query unexpected failure. raw={}", raw, e)
            src.reply("IP 查询失败：${e.message ?: "未知错误"}")
            0
        }
    }

    private data class IpCommandArgs(
        val ip: String,
        val providers: List<String>
    ) {

        companion object {

            fun parse(raw: String): IpCommandArgs {
                val tokens = raw.trim()
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }

                if (tokens.isEmpty()) {
                    throw IllegalArgumentException("请输入要查询的 IP 地址")
                }

                val providers = mutableListOf<String>()
                val ipTokens = mutableListOf<String>()

                var i = 0
                while (i < tokens.size) {
                    val token = tokens[i]

                    when {
                        token == "-p" || token == "--provider" || token == "--providers" -> {
                            val value = tokens.getOrNull(i + 1)
                                ?: throw IllegalArgumentException("$token 后需要填写数据源名称")
                            providers += parseProviderList(value)
                            i += 2
                        }

                        token.startsWith("--provider=") -> {
                            providers += parseProviderList(token.substringAfter("="))
                            i++
                        }

                        token.startsWith("--providers=") -> {
                            providers += parseProviderList(token.substringAfter("="))
                            i++
                        }

                        else -> {
                            ipTokens += token
                            i++
                        }
                    }
                }

                if (ipTokens.size != 1) {
                    throw IllegalArgumentException("请输入且只输入一个 IP 地址")
                }

                return IpCommandArgs(
                    ip = ipTokens.single(),
                    providers = providers.distinct()
                )
            }

            private fun parseProviderList(raw: String): List<String> {
                val providers = raw.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                if (providers.isEmpty()) {
                    throw IllegalArgumentException("数据源名称不能为空")
                }

                return providers
            }

        }

    }

}
