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
 * Functional interface representing the executable logic of a command leaf.
 *
 * Receives a [CommandInvocation] carrying the source, parsed arguments,
 * and raw command text. Returns an integer status code:
 *
 *   - `1` — success
 *   - `0` — argument error or business failure
 *
 *
 * @param S the source type
 */
fun interface CommandExecutor<S> {

    /**
     * Executes the command with the given invocation context.
     *
     * @param invocation the populated invocation context
     * @return 1 for success, 0 for failure
     */
    fun execute(invocation: CommandInvocation<S>): Int

}
