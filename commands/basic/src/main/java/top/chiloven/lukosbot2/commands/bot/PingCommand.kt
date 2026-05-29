/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.commands.bot

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
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
class PingCommand : IBotCommand {

    private val commandDefinition = botCommand("ping") {
        description = "返回机器人的运行状态与版本信息"

        execute {
            source.reply(buildStatus())
        }

        syntax("检测机器人在线状态")

        example("ping")
    }

    override fun definition() = commandDefinition

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
            TelegramBots: ${Constants.tgVersion} | JDA: ${Constants.jdaVersion}
        """.trimIndent()
    }

}
