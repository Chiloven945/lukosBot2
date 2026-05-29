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
package top.chiloven.lukosbot2.commands.definition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import top.chiloven.lukosbot2.core.command.definition.CommandParseException
import top.chiloven.lukosbot2.core.command.definition.parser.ShellWords

class ShellWordsTest {

    @Test
    fun split_empty() {
        assertEquals(emptyList<String>(), ShellWords.split(""))
    }

    @Test
    fun split_spaces() {
        assertEquals(listOf("a", "b", "c"), ShellWords.split("a b c"))
    }

    @Test
    fun split_double_quotes() {
        assertEquals(listOf("a b", "c"), ShellWords.split("\"a b\" c"))
    }

    @Test
    fun split_single_quotes() {
        assertEquals(listOf("a b", "c"), ShellWords.split("'a b' c"))
    }

    @Test
    fun split_unclosed_quote() {
        assertThrows<CommandParseException> {
            ShellWords.split("\"a b")
        }
        assertThrows<CommandParseException> {
            ShellWords.split("'a b")
        }
    }

    @Test
    fun split_equals_token() {
        assertEquals(
            listOf("/ip", "1.1.1.1", "--provider=ipsb"),
            ShellWords.split("/ip 1.1.1.1 --provider=ipsb")
        )
    }

    @Test
    fun split_extra_whitespace() {
        assertEquals(listOf("a", "b"), ShellWords.split("  a   b  "))
    }

    @Test
    fun split_quote_in_middle_of_word_is_literal() {
        assertEquals(listOf("a\\\"b"), ShellWords.split("a\\\"b"))
    }

}
