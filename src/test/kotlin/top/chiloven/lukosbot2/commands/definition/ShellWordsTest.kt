package top.chiloven.lukosbot2.commands.definition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        assertThrows<top.chiloven.lukosbot2.core.command.definition.CommandParseException> {
            ShellWords.split("\"a b")
        }
        assertThrows<top.chiloven.lukosbot2.core.command.definition.CommandParseException> {
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
