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

import top.chiloven.lukosbot2.core.command.definition.CommandParseException

/**
 * Splits a raw command tail into argv tokens using quote-aware word splitting.
 *
 * Supports double-quoted (`"..."`) and single-quoted (`'...'`)
 * segments. Quotes at non-word-boundaries are treated as literal characters.
 * Unclosed quotes throw [CommandParseException].
 *
 * This is **not** a full shell parser:
 *
 *   - No backslash escaping
 *   - No short-option combination (`-abc`)
 *   - No glob expansion
 *
 *
 * ### Examples
 * ```kotlin
 * ShellWords.split("a b c")       == ["a", "b", "c"]
 * ShellWords.split("\"a b\" c")   == ["a b", "c"]
 * ShellWords.split("--key=value") == ["--key=value"]
 * ShellWords.split("  a   b  ")   == ["a", "b"]
 * ```
 */
object ShellWords {

    /**
     * Tokenizes the input string into a list of words.
     *
     * @param input raw command tail
     * @return ordered list of tokens
     * @throws CommandParseException if a quote is not closed
     */
    fun split(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0

        while (i < input.length) {
            val c = input[i]
            when {
                c == '"' || c == '\'' -> {
                    if (current.isEmpty()) {
                        i = readQuoted(input, i, c, current)
                    } else {
                        current.append(c)
                        i++
                    }
                }

                c.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                    i++
                }

                else -> {
                    current.append(c)
                    i++
                }
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }

    private fun readQuoted(
        input: String,
        start: Int,
        quote: Char,
        current: StringBuilder
    ): Int {
        var i = start + 1
        while (i < input.length) {
            if (input[i] == quote) {
                return i + 1
            }
            current.append(input[i])
            i++
        }
        throw CommandParseException("引号未闭合", input = input, cursor = start)
    }

}
