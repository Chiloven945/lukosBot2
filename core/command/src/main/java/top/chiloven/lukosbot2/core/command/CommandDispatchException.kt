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
package top.chiloven.lukosbot2.core.command

/**
 * Thrown by [CommandRuntime] when the parsed input does not match
 * the command definition — for example, unknown subcommands, missing required
 * arguments, or unexpected extra arguments.
 *
 * Wrappers ([top.chiloven.lukosbot2.core.command.bot.BotCommandRuntime], [top.chiloven.lukosbot2.core.command.cli.CliCommandRuntime]) catch this
 * exception and format the message according to their platform conventions.
 *
 * @param path the literal path matched so far (e.g. `["github", "search"]`)
 * @param message the human-readable error cause
 */
class CommandDispatchException(
    val path: List<String>,
    override val message: String
) : RuntimeException(message)
