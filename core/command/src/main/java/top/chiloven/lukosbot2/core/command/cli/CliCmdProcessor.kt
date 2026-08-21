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
package top.chiloven.lukosbot2.core.command.cli

import org.apache.logging.log4j.LogManager
import top.chiloven.lukosbot2.util.StringUtils.indexOfWhitespace

class CliCmdProcessor(
    private val registry: CliCmdRegistry
) {

    private val log = LogManager.getLogger(CliCmdProcessor::class.java)

    init {
        for (cmd in registry.all()) {
            log.info(
                "[Cli] Registered cli command: {}",
                cmd.name() + (if (cmd.aliases().isEmpty()) "" else " aliases: " + cmd.aliases())
            )
        }
    }

    suspend fun handle(line: String?, ctx: CliCmdContext) {
        if (line.isNullOrBlank()) return

        val commandName = firstToken(line)
        val cmd = registry.get(commandName)
        if (cmd == null) {
            ctx.printlnErr("Unknown CLI command: $commandName")
            return
        }

        try {
            CliCommandRuntime.execute(cmd, ctx, line)
        } catch (e: Exception) {
            log.warn("[Cli] Cli command execution error: {}", e.message, e)
            ctx.printlnErr("Failed to execute CLI command: ${e.message}", e)
        }
    }

    private fun firstToken(input: String): String {
        val trimmed = input.trim()
        val ws = indexOfWhitespace(trimmed)
        return if (ws < 0) trimmed else trimmed.substring(0, ws)
    }

}
