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
package top.chiloven.lukosbot2.core.command.bot

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.core.IProcessor
import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.core.policy.PolicyService
import top.chiloven.lukosbot2.util.StringUtils.indexOfWhitespace
import top.chiloven.lukosbot2.util.message.TextExtractor

/**
 * Command processor using the project's own CommandRuntime.
 */
@Service
class CommandProcessor(
        commands: List<IBotCommand>?,
        props: AppProperties?,
        private val registry: CommandRegistry,
        private val policyService: PolicyService,
) : IProcessor {

    private val log = LogManager.getLogger(CommandProcessor::class.java)

    private val prefix: String = run {
        val p = props?.prefix
        if (p.isNullOrBlank()) "/" else p
    }

    init {
        commands?.forEach { cmd ->
            try {
                log.info(
                        $$"[Cmd] Registered command: $${cmd.name()}$${
                            if (cmd.aliases().isEmpty()) "" else " aliases: " + cmd.aliases()
                        }",
                )
            } catch (e: Exception) {
                log.warn(
                        "[Cmd] Failed to register command ${cmd.name()}: ${e.message}", e
                )
            }
        }
    }

    override suspend fun handle(inbound: InboundMessage): List<OutboundMessage> {
        val raw = TextExtractor.primaryText(inbound).trim()
        if (raw.isEmpty()) {
            return emptyList()
        }

        if (!raw.startsWith(prefix)) {
            return emptyList()
        }

        val cmdLine = raw.substring(prefix.length).trim()
        if (cmdLine.isEmpty()) return emptyList()

        val outs = ArrayList<OutboundMessage>()
        val src = CommandSource.forInbound(inbound, outs::add)

        val command = registry.get(firstToken(cmdLine))
                ?: return outs

        if (!policyService.isCommandAllowed(src, command.name())) {
            src.reply(policyService.commandDeniedMessage(command.name()))
            return outs
        }

        try {
            BotCommandRuntime.execute(command, src, cmdLine)
        } catch (e: Exception) {
            log.warn("[Cmd] Command execution error: {}", e.message, e)
            src.reply("命令执行失败，请稍后再试。")
        }

        return outs
    }

    private fun firstToken(cmdLine: String?): String {
        if (cmdLine == null) return ""
        val trimmed = cmdLine.trim()
        if (trimmed.isEmpty()) return ""
        val ws = indexOfWhitespace(trimmed)
        return if (ws < 0) trimmed else trimmed.substring(0, ws)
    }
}
