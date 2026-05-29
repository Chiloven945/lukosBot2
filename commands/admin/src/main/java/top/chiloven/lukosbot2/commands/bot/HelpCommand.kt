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

import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.*
import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.core.command.bot.CommandRegistry
import top.chiloven.lukosbot2.core.command.bot.CommandSource
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.arg
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.core.command.definition.dsl.lit
import top.chiloven.lukosbot2.core.command.definition.dsl.optOneOf
import top.chiloven.lukosbot2.core.policy.PolicyService

@Service
class HelpCommand(
    private val registryProvider: ObjectProvider<CommandRegistry>,
    private val appProperties: AppProperties,
    private val policyService: PolicyService
) : IBotCommand {

    private val commandDefinition = botCommand("help") {
        alias("h")
        description = "列出可用命令或其详细用法"

        argv {
            positional("command", ArgType.StringType) {
                required = false
                description = "命令名"
            }
            positional("mode", ArgType.StringType) {
                required = false
                description = "img 或 text"
            }
            execute { args ->
                val cmdName = args.getOrNull<String>("command")
                if (cmdName.isNullOrBlank()) {
                    showList(source)
                } else {
                    val mode = args.getOrNull<String>("mode")
                    showUsage(source, cmdName, mode)
                }
            }
        }

        syntax("列出所有可用命令")
        syntax(
            "查看命令用法（可选强制输出方式）",
            arg("command"),
            optOneOf(
                lit("img"),
                lit("text")
            )
        )
        param("command", "命令名（不带前缀），例如：wiki / music / github")

        example(
            "help",
            "help wiki",
            "help wiki img"
        )
        note(
            """
                输出方式：
                - `img`：强制输出图片版用法
                - `text`：强制输出文本版用法
                - 不指定时自动决定
                """.trimIndent()
        )
    }

    override fun definition() = commandDefinition

    private val p: String get() = appProperties.prefix.let { it.ifBlank { "/" } }

    private fun registry() = registryProvider.`object`

    private fun showList(src: CommandSource) {
        val sb = StringBuilder("可用命令：\n")
        registry().all().stream()
            .filter { it.isVisible }
            .filter { policyService.isCommandAllowed(src, it.name()) }
            .forEach { c ->
                sb.append(p).append(c.name())
                if (c.aliases().isNotEmpty()) sb.append(c.aliases())
                sb.append(" - ").append(c.description()).append("\n")
            }
        sb.append("\n发送 `").append(p).append(name()).append(" <command>` 查看某个命令的详细用法。")
        src.reply(sb.toString().trim())
    }

    private fun showUsage(
        src: CommandSource,
        cmdName: String,
        modeRaw: String?
    ): Int {
        val cmd = registry().get(cmdName)
        if (cmd == null || !cmd.isVisible) {
            src.reply("未知的命令：$cmdName\n发送 `$p${name()}` 查看可用命令列表。")
            return 0
        }
        if (!policyService.isCommandAllowed(src, cmd.name())) {
            src.reply(policyService.commandDeniedMessage(cmd.name()))
            return 0
        }

        val node: UsageNode = cmd.usage()
        val opt = UsageTextRenderer.Options.forHelp(p)
        UsageOutput.sendUsage(
            src,
            p,
            cmd.name(),
            node,
            opt,
            UsageImageUtils.ImageStyle.defaults(),
            UsageOutput.parseMode(modeRaw)
        )
        return 1
    }

}
