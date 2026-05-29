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
package top.chiloven.lukosbot2.core.command.definition.parser

/**
 * Holds the result of parsing argv-style command input.
 *
 * @param values map of canonicalName/name -> parsed value for all options and positionals
 * @param positionals the raw string tokens that were treated as positional arguments
 * @param unknownOptions reserved for future use (currently always empty)
 */
data class ArgvParseResult(
    val values: Map<String, Any?>,
    val positionals: List<String>,
    val unknownOptions: List<String> = emptyList()
) {

    /**
     * Retrieves a typed value by name. Throws if not present.
     *
     * @param name the argument name as declared in the DSL
     * @return the typed value
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String): T = values[name] as T

    /**
     * Retrieves a typed value by name, or null if not present.
     *
     * @param name the argument name
     * @return the typed value, or null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrNull(name: String): T? = values[name] as? T

}
