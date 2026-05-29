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
 * Specification for a single positional argument.
 *
 * Positional arguments are matched to tokens in order. The last positional
 * may be marked `greedy` to consume all remaining tokens.
 *
 * @param name the argument name used for retrieval and error messages
 * @param type the expected value type
 * @param required whether the argument must be present
 * @param defaultValue fallback value when not supplied (only for non-required)
 * @param greedy if true, consumes all remaining positional tokens
 * @param description human-readable description for usage output
 * @param choices set of allowed string values (validated after type conversion)
 * @param validator optional custom validation callback
 */
data class CommandArg(
    val name: String,
    val type: ArgType,
    val required: Boolean = true,
    val defaultValue: Any? = null,
    val greedy: Boolean = false,
    val description: String = "",
    val choices: List<String> = emptyList(),
    val validator: ValueValidator? = null
)
