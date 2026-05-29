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
package top.chiloven.lukosbot2.commands.bot.github.data

data class SearchParams(
    val keywords: String,
    val top: Int = 3,
    val language: String? = null,
    val sort: String? = null,
    val order: String? = null
) {

    companion object {

        fun parse(input: String): SearchParams {
            val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

            val opts: Map<String, String> = tokens.asSequence()
                .filter { it.startsWith("--") }
                .mapNotNull { t ->
                    val eq = t.indexOf('=')
                    if (eq > 2) t.substring(2, eq) to t.substring(eq + 1) else null
                }
                .toMap()

            val keywords = tokens.asSequence()
                .filter { t -> !t.startsWith("--") || t.indexOf('=') <= 2 }
                .joinToString(" ")
                .ifBlank { "java" }

            val top = opts["top"]?.toIntOrNull()?.coerceIn(1, 10) ?: 3

            return SearchParams(
                keywords = keywords,
                top = top,
                language = opts["lang"],
                sort = opts["sort"],
                order = opts["order"]
            )
        }

    }

}
