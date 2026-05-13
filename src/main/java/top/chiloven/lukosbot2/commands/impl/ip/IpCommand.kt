package top.chiloven.lukosbot2.commands.impl.ip

import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.definition.ArgType
import top.chiloven.lukosbot2.commands.definition.CommandInvocation
import top.chiloven.lukosbot2.commands.definition.bridge.SpecBotCommand
import top.chiloven.lukosbot2.commands.definition.dsl.arg
import top.chiloven.lukosbot2.commands.definition.dsl.botCommand
import top.chiloven.lukosbot2.commands.impl.ip.IpQueryResult.IpQueryException

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["ip"],
    havingValue = "true",
    matchIfMissing = true
)
class IpCommand(
    private val ipQueryService: IpQueryService
) : SpecBotCommand() {

    private val log = LogManager.getLogger(IpCommand::class.java)

    override fun spec() = botCommand("ip") {
        description = "查询 IP 信息"

        argv {
            positional("ip", ArgType.StringType) {
                required = true
                description = "IP 地址"
            }

            option("providers") {
                names = listOf("-p", "--provider", "--providers")
                type = ArgType.StringType
                splitBy = ","
                description = "指定数据源；多个用逗号分隔"
            }

            execute { args ->
                runQuery(
                    ip = args.get("ip"),
                    providers = args.getOrNull<List<String>>("providers") ?: emptyList()
                )
            }
        }

        syntax("按默认数据源优先级查询", arg("ip_address"))
        param("ip_address", "IP 地址（IPv4 / IPv6）")
        example(
            "ip 1.1.1.1",
            "ip 2606:4700:4700::1111",
            "ip --provider=ipquery 1.1.1.1",
            "ip --provider=ipquery,ipsb 1.1.1.1"
        )
    }

    private fun CommandInvocation.runQuery(ip: String, providers: List<String>) {
        try {
            val result = ipQueryService.query(
                ip = ip,
                requestedProviders = providers
            )
            reply(result.toDisplayText())
        } catch (e: IllegalArgumentException) {
            reply(e.message ?: "参数错误，请检查 IP 地址或数据源名称")
        } catch (e: IpQueryException) {
            log.warn("IP query failed. ip={}", e.ip, e)
            reply(e.message ?: "IP 查询失败，请稍后重试")
        } catch (e: Exception) {
            log.warn("IP query unexpected failure. ip={}", ip, e)
            reply("IP 查询失败：${e.message ?: "未知错误"}")
        }
    }

}
