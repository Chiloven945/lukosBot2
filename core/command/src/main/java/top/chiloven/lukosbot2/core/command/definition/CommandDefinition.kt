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
package top.chiloven.lukosbot2.core.command.definition

/**
 * The top-level definition of a command, shared between bot and CLI subsystems.
 *
 * A [CommandDefinition] describes the complete structure of one command:
 * its primary name, optional aliases, human-readable description, visibility flag,
 * and a recursive tree of literal nodes ([CommandNode]) that define
 * subcommands and execution leaves.
 *
 * Use the DSL builder functions to construct definitions declaratively:
 *
 * ```kotlin
 * botCommand("ping") {           // CommandDefinition<CommandSource>
 *     description = "Check status"
 *     execute { source.reply("pong!") }
 * }
 *
 * cliCommand("shutdown") {      // CommandDefinition<CliCmdContext>
 *     alias("stop", "close")
 *     description = "Shutdown the bot"
 *     execute { Main.shutdown() }
 * }
 * ```
 *
 * @param S the source type — `CommandSource` for bot commands,
 *          `CliCmdContext` for CLI commands
 * @param name canonical command name, used for registration and lookup
 * @param aliases alternative invocation names (case-insensitive)
 * @param description short human-readable summary
 * @param visible whether the command appears in help listings
 * @param root the root literal node of the command tree
 */
data class CommandDefinition<S>(
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String,
    val visible: Boolean = true,
    val root: CommandNode<S>
)
