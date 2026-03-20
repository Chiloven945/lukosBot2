package top.chiloven.lukosbot2.commands.impl

import com.mojang.brigadier.CommandDispatcher
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.StringUtils
import top.chiloven.lukosbot2.util.TimeUtils
import top.chiloven.lukosbot2.util.brigadier.builder.LiteralArgumentBuilder.literal
import java.lang.management.ManagementFactory
import java.time.Instant
import java.time.ZoneId

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["ping"],
    havingValue = "true",
    matchIfMissing = true
)
class PingCommand : IBotCommand {

    override fun name(): String = "ping"

    override fun usage(): UsageNode =
        UsageNode.root(name())
            .description(description())
            .syntax("检测机器人在线状态")
            .example("ping")
            .build()

    override fun description(): String = "返回机器人的运行状态与版本信息"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    ctx.source.reply(buildStatus())
                    1
                }
        )
    }

    private fun buildStatus(): String {
        val runtime = Runtime.getRuntime()

        val totalMem = StringUtils.fmtBytes(runtime.totalMemory(), 2)
        val maxMem = StringUtils.fmtBytes(runtime.maxMemory(), 2)
        val usedMem = StringUtils.fmtBytes(runtime.totalMemory() - runtime.freeMemory(), 2)

        val rtBean = ManagementFactory.getRuntimeMXBean()
        val uptimeFmt = TimeUtils.formatUptime(rtBean.uptime / 1000)

        val osBean = ManagementFactory.getOperatingSystemMXBean()
        val time = TimeUtils.dtf().withZone(ZoneId.systemDefault()).format(Instant.now())

        return """
            Pong！$time
            ${Constants.APP_NAME} ${Constants.VERSION}
            运行时间: $uptimeFmt
            系统: ${osBean.name} ${osBean.version}
            内存: $usedMem / $totalMem (最大 $maxMem)
            Java: ${Constants.javaVersion} | SpringBoot: ${Constants.springBootVersion}
            TelegramBots: ${Constants.tgVersion} | JDA: ${Constants.jdaVersion} | Shiro: ${Constants.shiroVersion}
        """.trimIndent()
    }

}
