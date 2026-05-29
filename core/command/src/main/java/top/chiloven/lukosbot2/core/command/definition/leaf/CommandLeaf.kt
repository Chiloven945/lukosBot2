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

import top.chiloven.lukosbot2.core.command.definition.CommandExecutor

/**
 * Sealed interface for command leaf execution strategies.
 *
 * A leaf is reached by the runtime when no child literal matches the remaining
 * input at the current path position. The runtime then dispatches to the leaf's
 * executor according to the leaf type's strategy:
 *
 *   - [EmptyLeaf] — no arguments expected; raw tail must be empty
 *   - [RawLeaf] — passes the entire raw tail as a single string
 *   - [ArgvLeaf] — tokenizes the tail via `ShellWords` and
 *     parses with `ArgvParser` into named positional and option values
 *   - [TreeLeaf] — parses the tail as a list of positional arguments
 *
 * @param S the source type
 */
sealed interface CommandLeaf<S> {

    /**
     * The callable that processes a [top.chiloven.lukosbot2.core.command.definition.CommandInvocation]
     */
    val executor: CommandExecutor<S>

}
