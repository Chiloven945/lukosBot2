package top.chiloven.lukosbot2.commands.impl

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.commands.spec.bridge.SpecBotCommand
import top.chiloven.lukosbot2.commands.spec.dsl.botCommand
import top.chiloven.lukosbot2.util.StringUtils
import top.chiloven.lukosbot2.util.TimeUtils
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
class PingCommand : SpecBotCommand() {

    override fun spec() = botCommand("ping") {
        description = "返回机器人的运行状态与版本信息"

        execute {
            reply(buildStatus())
        }

        syntax("检测机器人在线状态")
        example("ping")
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
            运行时间：$uptimeFmt
            系统：${osBean.name} ${osBean.version}
            内存：$usedMem / $totalMem（最大 $maxMem）
            Java：${Constants.javaVersion} | Kotlin：${Constants.kotlinVersion} | Spring Boot：${Constants.springBootVersion}
            TelegramBots: ${Constants.tgVersion} | JDA: ${Constants.jdaVersion} | Shiro: ${Constants.shiroVersion}
        """.trimIndent()
    }

}
