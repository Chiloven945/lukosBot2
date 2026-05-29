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

import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.CommandDispatchException
import top.chiloven.lukosbot2.core.command.CommandRuntime

/**
 * Bot-specific wrapper around [CommandRuntime] that catches dispatch
 * errors and formats them with Chinese-language messages suitable for
 * chat-based platforms (Telegram, Discord).
 *
 * ## Error format
 *
 * ```text
 * 命令参数错误：
 * <cause>
 *
 * 发送 /help <path> 查看详细用法。
 * ```
 */
object BotCommandRuntime {

    /**
     * Executes a bot command using the given source and raw command line.
     *
     * The raw command line should have the global prefix (e.g. `"/"`)
     * already stripped. The first token is matched case-insensitively against
     * the command's name and aliases via [IBotCommand.matches].
     *
     * @param command the bot command to execute
     * @param source the chat source for replies
     * @param rawCommandLine the command text without the prefix
     * @return 1 on success, 0 on error (with a reply sent to source)
     */
    fun execute(command: IBotCommand, source: CommandSource, rawCommandLine: String): Int {
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
            source.reply(
                """
                命令参数错误：${e.message}

                发送 /help ${e.path.joinToString(" ")} 查看详细用法。
                """.trimIndent()
            )
            0
        }
    }

}
