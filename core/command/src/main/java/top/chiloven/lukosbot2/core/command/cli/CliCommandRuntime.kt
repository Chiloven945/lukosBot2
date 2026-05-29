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

import top.chiloven.lukosbot2.commands.ICliCommand
import top.chiloven.lukosbot2.core.command.CommandDispatchException
import top.chiloven.lukosbot2.core.command.CommandRuntime

/**
 * CLI-specific wrapper around [CommandRuntime] that catches dispatch
 * errors and formats them in English for console output via `CliCmdContext`.
 *
 * ## Error format
 *
 * ```text
 * CLI command syntax error:
 * <cause>
 *
 * Usage: <path>
 * ```
 */
object CliCommandRuntime {

    /**
     * Executes a CLI command using the given context and raw command line.
     *
     * The first token is matched case-insensitively against the command's
     * name and aliases via [ICliCommand.matches].
     *
     * @param command the CLI command to execute
     * @param source the console context for output
     * @param rawCommandLine the full command line text
     * @return 1 on success, 0 on error (with a message printed to source)
     */
    fun execute(command: ICliCommand, source: CliCmdContext, rawCommandLine: String): Int {
        val rootToken = CommandRuntime.firstToken(rawCommandLine.trim()) ?: return 0
        if (!command.matches(rootToken)) return 0

        return try {
            CommandRuntime.execute(
                root = command.definition().root,
                rawTail = CommandRuntime.stripFirstToken(rawCommandLine),
                source = source,
                path = listOf(command.name())
            )
        } catch (e: CommandDispatchException) {
            source.printlnErr(
                """
                CLI command syntax error:
                ${e.message}

                Usage: ${e.path.joinToString(" ")}
                """.trimIndent()
            )
            0
        }
    }

}
