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
package top.chiloven.lukosbot2.core.command.definition.leaf

import top.chiloven.lukosbot2.core.command.definition.CommandArg
import top.chiloven.lukosbot2.core.command.definition.CommandExecutor
import top.chiloven.lukosbot2.core.command.definition.CommandOption

/**
 * Leaf that tokenizes the tail via `ShellWords` and parses into named
 * positional arguments and options using `ArgvParser`.
 *
 * This is the most common leaf type for commands with structured arguments.
 * Supports `--key=value`, `-f`, boolean flags, split lists,
 * repeatable options, greedy positionals, and more.
 *
 * @param positionals ordered list of positional argument specs
 * @param options list of option/flag specs
 */
data class ArgvLeaf<S>(
    val positionals: List<CommandArg>,
    val options: List<CommandOption>,
    override val executor: CommandExecutor<S>
) : CommandLeaf<S>
