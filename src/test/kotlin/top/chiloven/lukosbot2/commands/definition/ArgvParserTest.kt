package top.chiloven.lukosbot2.commands.definition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.CommandArg
import top.chiloven.lukosbot2.core.command.definition.CommandOption
import top.chiloven.lukosbot2.core.command.definition.CommandParseException
import top.chiloven.lukosbot2.core.command.definition.parser.ArgvParser
import top.chiloven.lukosbot2.core.command.definition.parser.ShellWords

class ArgvParserTest {

    @Test
    fun parse_long_option_equals() {
        val opts = listOf(
            CommandOption(
                "provider",
                listOf("--provider"),
                ArgType.StringType
            )
        )
        val result = ArgvParser.parse(
            tokens = ShellWords.split("--provider=ipsb"),
            positionals = emptyList(),
            options = opts
        )
        assertEquals("ipsb", result.get("provider"))
    }

    @Test
    fun parse_long_option_space() {
        val opts = listOf(
            CommandOption(
                "provider",
                listOf("--provider"),
                ArgType.StringType
            )
        )
        val result = ArgvParser.parse(
            tokens = ShellWords.split("--provider ipsb"),
            positionals = emptyList(),
            options = opts
        )
        assertEquals("ipsb", result.get("provider"))
    }

    @Test
    fun parse_short_option() {
        val opts = listOf(
            CommandOption(
                "provider",
                listOf("-p", "--provider"),
                ArgType.StringType
            )
        )
        val result = ArgvParser.parse(
            tokens = ShellWords.split("-p ipsb"),
            positionals = emptyList(),
            options = opts
        )
        assertEquals("ipsb", result.get("provider"))
    }

    @Test
    fun parse_boolean_flag() {
        val opts = listOf(
            CommandOption(
                "verbose",
                listOf("-v", "--verbose"),
                ArgType.BooleanType
            )
        )
        val result = ArgvParser.parse(
            tokens = ShellWords.split("--verbose"),
            positionals = emptyList(),
            options = opts
        )
        assertEquals(true, result.get<Boolean>("verbose"))
    }

    @Test
    fun parse_split_list() {
        val opts = listOf(
            CommandOption(
                "providers",
                listOf("--providers"),
                ArgType.StringType,
                splitBy = ","
            )
        )
        val result = ArgvParser.parse(
            tokens = ShellWords.split("--providers=ipsb,ipquery"),
            positionals = emptyList(),
            options = opts
        )

        @Suppress("UNCHECKED_CAST")
        val list = result.get<List<String>>("providers")
        assertEquals(listOf("ipsb", "ipquery"), list)
    }

    @Test
    fun parse_required_positional() {
        val positionals = listOf(
            CommandArg(
                "ip",
                ArgType.StringType,
                required = true
            )
        )
        val result = ArgvParser.parse(
            tokens = ShellWords.split("1.1.1.1"),
            positionals = positionals,
            options = emptyList()
        )
        assertEquals("1.1.1.1", result.get("ip"))
    }

    @Test
    fun parse_optional_positional_default() {
        val positionals = listOf(
            CommandArg(
                "count",
                ArgType.LongType,
                required = false,
                defaultValue = 1L
            )
        )
        val result = ArgvParser.parse(
            tokens = emptyList(),
            positionals = positionals,
            options = emptyList()
        )
        assertEquals(1L, result.get<Long>("count"))
    }

    @Test
    fun parse_greedy_positional() {
        val positionals = listOf(
            CommandArg(
                "keyword",
                ArgType.StringType,
                greedy = true
            )
        )
        val result = ArgvParser.parse(
            tokens = ShellWords.split("hello world kotlin"),
            positionals = positionals,
            options = emptyList()
        )
        assertEquals("hello world kotlin", result.get("keyword"))
    }

    @Test
    fun unknown_option() {
        val ex = assertThrows<CommandParseException> {
            ArgvParser.parse(
                tokens = ShellWords.split("--foo bar"),
                positionals = emptyList(),
                options = emptyList()
            )
        }
        assertTrue(ex.message!!.contains("未知参数"))
    }

    @Test
    fun missing_option_value() {
        val opts = listOf(
            CommandOption(
                "provider",
                listOf("--provider"),
                ArgType.StringType
            )
        )
        val ex = assertThrows<CommandParseException> {
            ArgvParser.parse(
                tokens = ShellWords.split("--provider"),
                positionals = emptyList(),
                options = opts
            )
        }
        assertTrue(ex.message!!.contains("需要一个值"))
    }

    @Test
    fun invalid_int() {
        val positionals = listOf(
            CommandArg(
                "count",
                ArgType.IntType
            )
        )
        assertThrows<NumberFormatException> {
            ArgvParser.parse(
                tokens = ShellWords.split("abc"),
                positionals = positionals,
                options = emptyList()
            )
        }
    }

    @Test
    fun invalid_choice() {
        val opts = listOf(
            CommandOption(
                "sort",
                listOf("--sort"),
                ArgType.StringType,
                choices = listOf("stars", "updated")
            )
        )
        val ex = assertThrows<CommandParseException> {
            ArgvParser.parse(
                tokens = ShellWords.split("--sort=forks"),
                positionals = emptyList(),
                options = opts
            )
        }
        assertTrue(ex.message!!.contains("可选值"))
    }

    @Test
    fun too_many_positionals() {
        val positionals = listOf(
            CommandArg(
                "ip",
                ArgType.StringType,
                required = true
            )
        )
        assertThrows<CommandParseException> {
            ArgvParser.parse(
                tokens = ShellWords.split("1.1.1.1 2.2.2.2"),
                positionals = positionals,
                options = emptyList()
            )
        }
    }

    @Test
    fun parse_mixed() {
        val positionals = listOf(
            CommandArg(
                "ip",
                ArgType.StringType,
                required = true
            )
        )
        val opts = listOf(
            CommandOption(
                "providers",
                listOf("-p", "--providers"),
                ArgType.StringType,
                splitBy = ","
            )
        )
        val result = ArgvParser.parse(
            tokens = ShellWords.split("-p ipsb,ipquery 1.1.1.1"),
            positionals = positionals,
            options = opts
        )
        assertEquals("1.1.1.1", result.get("ip"))
        @Suppress("UNCHECKED_CAST")
        val providers = result.get<List<String>>("providers")
        assertEquals(listOf("ipsb", "ipquery"), providers)
    }

}
