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
 * Leaf that expects no arguments.
 *
 * The raw tail must be empty; otherwise the runtime returns a parse error.
 * Used for commands like `/ping` or `/start`.
 */
data class EmptyLeaf<S>(
    override val executor: CommandExecutor<S>
) : CommandLeaf<S>
