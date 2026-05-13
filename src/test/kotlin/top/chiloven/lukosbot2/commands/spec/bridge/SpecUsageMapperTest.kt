package top.chiloven.lukosbot2.commands.spec.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.commands.spec.*

class SpecUsageMapperTest {

    private val noop = CommandExecutor { 1 }

    @Test
    fun ping_usage() {
        val spec = BotCommandSpec(
            name = "ping",
            description = "return bot status and version info",
            root = CommandNodeSpec(
                name = "ping",
                description = "return bot status and version info",
                syntaxes = listOf(
                    CommandSyntaxSpec(
                        description = "Check bot online status"
                    )
                ),
                examples = listOf("ping"),
                leaf = EmptyLeafSpec(noop)
            )
        )

        val node = SpecUsageMapper.toUsageNode(spec)

        assertEquals("ping", node.name)
        assertEquals("return bot status and version info", node.description)
        assertEquals(1, node.examples.size)
        assertEquals("ping", node.examples[0])
        assertEquals(1, node.syntaxes.size)
    }

    @Test
    fun dice_usage() {
        val spec = BotCommandSpec(
            name = "dice",
            description = "roll dice, optionally specify count",
            root = CommandNodeSpec(
                name = "dice",
                description = "roll dice, optionally specify count",
                examples = listOf("dice", "dice 3"),
                leaf = ArgvLeafSpec(
                    positionals = listOf(
                        CommandArgSpec(
                            "count",
                            ArgType.LongType,
                            required = false,
                            defaultValue = 1L,
                            description = "dice count"
                        )
                    ),
                    options = emptyList(),
                    executor = noop
                )
            )
        )

        val node = SpecUsageMapper.toUsageNode(spec)

        assertEquals("dice", node.name)
        assertEquals(2, node.examples.size)
        assertTrue(node.syntaxes.isNotEmpty())

        val autoSyntax = node.syntaxes.first()
        assertTrue(autoSyntax.tailText().contains("count"))
    }

    @Test
    fun ip_usage() {
        val spec = BotCommandSpec(
            name = "ip",
            description = "query IP info",
            root = CommandNodeSpec(
                name = "ip",
                description = "query IP info",
                examples = listOf("ip 1.1.1.1", "ip --provider=ipsb 1.1.1.1"),
                leaf = ArgvLeafSpec(
                    positionals = listOf(
                        CommandArgSpec(
                            "ip",
                            ArgType.StringType,
                            required = true,
                            description = "IP address"
                        )
                    ),
                    options = listOf(
                        CommandOptionSpec(
                            "providers",
                            listOf("-p", "--provider", "--providers"),
                            ArgType.StringType,
                            splitBy = ",",
                            description = "specify data sources"
                        )
                    ),
                    executor = noop
                )
            )
        )

        val node = SpecUsageMapper.toUsageNode(spec)

        assertEquals("ip", node.name)
        assertEquals(2, node.examples.size)

        val autoSyntax = node.syntaxes.first()
        assertTrue(autoSyntax.tailText().contains("ip"))
        assertTrue(autoSyntax.tailText().contains("provider"))

        val params = node.parameters
        assertEquals(1, params.size)
        assertEquals("<ip>", UsageNode.renderItem(params[0].token))
        assertEquals("IP address", params[0].description)

        val options = node.options
        assertEquals(1, options.size)
        assertEquals("specify data sources", options[0].description)
    }

    @Test
    fun github_nested_usage() {
        val spec = BotCommandSpec(
            name = "github",
            aliases = listOf("gh"),
            description = "GitHub search tools",
            root = CommandNodeSpec(
                name = "github",
                description = "GitHub search tools",
                children = listOf(
                    CommandNodeSpec(
                        name = "user",
                        description = "query user info",
                        examples = listOf("github user GitHub"),
                        leaf = RawLeafSpec("username", required = true, executor = noop)
                    ),
                    CommandNodeSpec(
                        name = "search",
                        description = "search repos",
                        examples = listOf("github search lukosbot"),
                        leaf = ArgvLeafSpec(
                            positionals = listOf(
                                CommandArgSpec(
                                    "keyword",
                                    ArgType.StringType,
                                    required = true,
                                    greedy = true,
                                    description = "search keyword"
                                )
                            ),
                            options = listOf(
                                CommandOptionSpec(
                                    "top",
                                    listOf("--top"),
                                    ArgType.IntType,
                                    description = "result count"
                                )
                            ),
                            executor = noop
                        )
                    )
                )
            )
        )

        val node = SpecUsageMapper.toUsageNode(spec)

        assertEquals("github", node.name)
        assertEquals(listOf("gh"), node.aliases)
        assertEquals(2, node.children.size)

        val userChild = node.children.find { it.name == "user" }
        assertTrue(userChild != null)
        assertEquals("query user info", userChild!!.description)
        assertTrue(userChild.syntaxes.first().tailText().contains("username"))

        val searchChild = node.children.find { it.name == "search" }
        assertTrue(searchChild != null)
        assertEquals("search repos", searchChild!!.description)
        assertTrue(searchChild.syntaxes.first().tailText().contains("keyword"))
    }

    @Test
    fun argv_options_render() {
        val spec = BotCommandSpec(
            name = "cmd",
            description = "test",
            root = CommandNodeSpec(
                name = "cmd",
                leaf = ArgvLeafSpec(
                    positionals = listOf(
                        CommandArgSpec("input", ArgType.StringType, required = true)
                    ),
                    options = listOf(
                        CommandOptionSpec(
                            "verbose",
                            listOf("-v", "--verbose"),
                            ArgType.BooleanType,
                            description = "enable verbose"
                        ),
                        CommandOptionSpec(
                            "count",
                            listOf("-n", "--count"),
                            ArgType.IntType,
                            description = "item count"
                        )
                    ),
                    executor = noop
                )
            )
        )

        val node = SpecUsageMapper.toUsageNode(spec)

        val syntaxText = node.syntaxes.first().tailText()
        assertTrue(syntaxText.contains("input"))
        assertTrue(syntaxText.contains("verbose"))
        assertTrue(syntaxText.contains("count"))

        assertEquals(2, node.options.size)
        val optionNames = node.options.map {
            UsageNode.renderItem(it.token)
        }
        assertTrue(optionNames.any { it.contains("verbose") })
        assertTrue(optionNames.any { it.contains("count") })
    }

    @Test
    fun aliases_render() {
        val spec = BotCommandSpec(
            name = "github",
            aliases = listOf("gh", "git"),
            description = "GitHub tools",
            root = CommandNodeSpec(
                name = "github",
                description = "GitHub tools",
                leaf = EmptyLeafSpec(noop)
            )
        )

        val node = SpecUsageMapper.toUsageNode(spec)

        assertEquals("github", node.name)
        assertEquals(2, node.aliases.size)
        assertTrue(node.aliases.contains("gh"))
        assertTrue(node.aliases.contains("git"))
    }

    @Test
    fun tree_leaf_usage() {
        val spec = BotCommandSpec(
            name = "calc",
            description = "calculate something",
            root = CommandNodeSpec(
                name = "calc",
                description = "calculate something",
                leaf = TreeLeafSpec(
                    arguments = listOf(
                        CommandArgSpec("a", ArgType.IntType, required = true),
                        CommandArgSpec("b", ArgType.IntType, required = false, defaultValue = 0)
                    ),
                    executor = noop
                )
            )
        )

        val node = SpecUsageMapper.toUsageNode(spec)

        val syntaxText = node.syntaxes.first().tailText()
        assertTrue(syntaxText.contains("<a>"))
        assertTrue(syntaxText.contains("[<b>]"))
        assertEquals(2, node.parameters.size)
    }

    @Test
    fun empty_leaf_no_tail() {
        val spec = BotCommandSpec(
            name = "ping",
            description = "pong",
            root = CommandNodeSpec(
                name = "ping",
                description = "pong",
                leaf = EmptyLeafSpec(noop)
            )
        )

        val node = SpecUsageMapper.toUsageNode(spec)

        assertEquals(1, node.syntaxes.size)
        assertTrue(node.syntaxes.first().tailText().isEmpty())
    }
}
