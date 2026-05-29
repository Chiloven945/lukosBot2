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
 * Specification for a named option/flag.
 *
 * Options are prefixed with `--` or `-` and may appear anywhere
 * among positional tokens. Supported forms:
 *
 *   - `--key=value` or `--key value`
 *   - `-n value`
 *   - `-f` (boolean flag, no value consumed)
 *   - `--providers=a,b,c` with `splitBy`
 *
 *
 * @param canonicalName the unique key used for retrieval
 * @param names accepted names including prefixes (e.g. `["-p", "--provider"]`)
 * @param type the expected value type
 * @param required whether the option must be present
 * @param defaultValue fallback value when not supplied
 * @param repeatable whether the option can appear multiple times (accumulates into a list)
 * @param splitBy if set, splits the value by this delimiter into a list
 * @param description human-readable description for usage output
 * @param choices set of allowed string values
 * @param validator optional custom validation callback
 */
data class CommandOption(
    val canonicalName: String,
    val names: List<String>,
    val type: ArgType,
    val required: Boolean = false,
    val defaultValue: Any? = null,
    val repeatable: Boolean = false,
    val splitBy: String? = null,
    val description: String = "",
    val choices: List<String> = emptyList(),
    val validator: ValueValidator? = null
)
