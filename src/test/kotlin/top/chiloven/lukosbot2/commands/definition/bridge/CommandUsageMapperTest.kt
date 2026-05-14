package top.chiloven.lukosbot2.commands.definition.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.commands.definition.*
import top.chiloven.lukosbot2.core.command.CommandSource

class CommandUsageMapperTest {

    private val noop = CommandExecutor<CommandSource> { 1 }

    @Test
    fun ping_usage() {
        val spec = CommandDefinition(
            name = "ping",
            description = "return bot status",
            root = CommandNodeSpec(
                name = "ping",
                syntaxes = listOf(CommandSyntaxSpec(description = "Check status")),
                examples = listOf("ping"),
                leaf = EmptyLeafSpec(noop)
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertEquals("ping", node.name)
        assertEquals(1, node.examples.size)
    }

    @Test
    fun dice_usage() {
        val spec = CommandDefinition(
            name = "dice",
            description = "roll dice",
            root = CommandNodeSpec(
                name = "dice",
                examples = listOf("dice", "dice 3"),
                leaf = ArgvLeafSpec(
                    positionals = listOf(
                        CommandArgSpec(
                            "count",
                            ArgType.LongType,
                            required = false,
                            defaultValue = 1L
                        )
                    ),
                    options = emptyList(),
                    executor = noop
                )
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertEquals("dice", node.name)
        assertTrue(node.syntaxes.first().tailText().contains("count"))
    }

    @Test
    fun ip_usage() {
        val spec = CommandDefinition(
            name = "ip",
            description = "query IP",
            root = CommandNodeSpec(
                name = "ip",
                examples = listOf("ip 1.1.1.1"),
                leaf = ArgvLeafSpec(
                    positionals = listOf(CommandArgSpec("ip", ArgType.StringType, required = true)),
                    options = listOf(
                        CommandOptionSpec(
                            "providers",
                            listOf("--provider"),
                            ArgType.StringType,
                            splitBy = ","
                        )
                    ),
                    executor = noop
                )
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertTrue(node.syntaxes.first().tailText().contains("ip"))
    }

    @Test
    fun github_nested_usage() {
        val spec = CommandDefinition(
            name = "github",
            aliases = listOf("gh"),
            description = "GitHub",
            root = CommandNodeSpec(
                name = "github",
                children = listOf(
                    CommandNodeSpec(
                        name = "user",
                        examples = listOf("github user x"),
                        leaf = RawLeafSpec("username", required = true, executor = noop)
                    ),
                    CommandNodeSpec(
                        name = "search",
                        examples = listOf("github search x"),
                        leaf = ArgvLeafSpec(
                            positionals = listOf(
                                CommandArgSpec(
                                    "keyword",
                                    ArgType.StringType,
                                    required = true,
                                    greedy = true
                                )
                            ),
                            options = listOf(
                                CommandOptionSpec(
                                    "top",
                                    listOf("--top"),
                                    ArgType.IntType
                                )
                            ),
                            executor = noop
                        )
                    )
                )
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertEquals("github", node.name)
        assertEquals(listOf("gh"), node.aliases)
        assertEquals(2, node.children.size)
    }

    @Test
    fun argv_options_render() {
        val spec = CommandDefinition(
            name = "cmd",
            description = "test",
            root = CommandNodeSpec(
                name = "cmd",
                leaf = ArgvLeafSpec(
                    positionals = listOf(
                        CommandArgSpec(
                            "input",
                            ArgType.StringType,
                            required = true
                        )
                    ),
                    options = listOf(
                        CommandOptionSpec(
                            "verbose",
                            listOf("-v"),
                            ArgType.BooleanType
                        ),
                        CommandOptionSpec(
                            "count",
                            listOf("-n"),
                            ArgType.IntType
                        )
                    ),
                    executor = noop
                )
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)
        val syntaxText = node.syntaxes.first().tailText()

        assertTrue(syntaxText.contains("input"))
    }

    @Test
    fun aliases_render() {
        val spec = CommandDefinition(
            name = "github",
            aliases = listOf("gh", "git"),
            description = "GitHub tools",
            root = CommandNodeSpec(
                name = "github",
                leaf = EmptyLeafSpec(noop)
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertEquals(2, node.aliases.size)
    }

    @Test
    fun tree_leaf_usage() {
        val spec = CommandDefinition(
            name = "calc",
            description = "calculate",
            root = CommandNodeSpec(
                name = "calc",
                leaf = TreeLeafSpec(
                    arguments = listOf(
                        CommandArgSpec("a", ArgType.IntType, required = true),
                        CommandArgSpec("b", ArgType.IntType, required = false)
                    ),
                    executor = noop
                )
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)
        val text = node.syntaxes.first().tailText()

        assertTrue(text.contains("<a>"))
        assertTrue(text.contains("[<b>]"))
    }

    @Test
    fun empty_leaf_no_tail() {
        val spec = CommandDefinition(
            name = "ping",
            description = "pong",
            root = CommandNodeSpec(
                name = "ping",
                leaf = EmptyLeafSpec(noop)
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertEquals(1, node.syntaxes.size)
        assertTrue(node.syntaxes.first().tailText().isEmpty())
    }

}
