package top.chiloven.lukosbot2.core.command.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.definition.*
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.model.message.Address
import top.chiloven.lukosbot2.model.message.outbound.OutText
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.platform.ChatPlatform

class CommandRuntimeTest {

    companion object {

        private fun extractText(out: OutboundMessage): String {
            val part = out.parts()?.firstOrNull()
            return if (part is OutText) part.text() else ""
        }

    }

    private fun fakeSource(): FakeSource = FakeSource()

    private class FakeSource {

        val replies = mutableListOf<String>()
        val source: CommandSource = CommandSource.forAddress(
            Address(ChatPlatform.TELEGRAM, 123L, false)
        ) { out -> replies += extractText(out) }

    }

    private fun asCommand(spec: CommandDefinition<CommandSource>): IBotCommand =
        IBotCommand { spec }

    @Test
    fun execute_empty_leaf() {
        val replies = mutableListOf<String>()
        val src = CommandSource.forAddress(
            Address(
                ChatPlatform.TELEGRAM,
                1L,
                false
            )
        ) { out -> replies += extractText(out) }

        val spec = CommandDefinition(
            name = "ping",
            description = "pong",
            root = CommandNodeSpec<CommandSource>(
                name = "ping",
                leaf = EmptyLeafSpec(CommandExecutor { it.source.reply("pong!"); 1 })
            )
        )

        val result = BotCommandRuntime.execute(
            asCommand(spec),
            src,
            "ping"
        )
        assertEquals(1, result)
        assertEquals(listOf("pong!"), replies)

        val result2 = BotCommandRuntime.execute(
            asCommand(spec),
            src,
            "ping extra"
        )
        assertEquals(0, result2)
        assertTrue(replies[1].contains("不需要参数"))
    }

    @Test
    fun execute_raw_leaf() {
        val s = fakeSource()
        val spec = CommandDefinition(
            name = "echo",
            description = "echo",
            root = CommandNodeSpec(
                name = "echo",
                leaf = RawLeafSpec(
                    name = "text",
                    required = true,
                    executor = CommandExecutor<CommandSource> { inv ->
                        inv.source.reply(inv.rawTail)
                        1
                    }
                )
            )
        )
        val result = BotCommandRuntime.execute(
            asCommand(spec),
            s.source,
            "echo hello world"
        )
        assertEquals(1, result)
        assertEquals(listOf("hello world"), s.replies)
    }

    @Test
    fun execute_required_raw_missing() {
        val s = fakeSource()
        val spec = CommandDefinition(
            name = "echo",
            description = "echo",
            root = CommandNodeSpec(
                name = "echo",
                leaf = RawLeafSpec(
                    name = "text",
                    required = true,
                    executor = CommandExecutor<CommandSource> { inv ->
                        inv.source.reply(inv.rawTail)
                        1
                    }
                )
            )
        )
        val result = BotCommandRuntime.execute(
            asCommand(spec),
            s.source,
            "echo"
        )

        assertEquals(0, result)
        assertTrue(s.replies[0].contains("缺少必填参数"))
    }

    @Test
    fun execute_argv_leaf() {
        val s = fakeSource()
        val spec = CommandDefinition(
            name = "dice",
            description = "roll dice",
            root = CommandNodeSpec(
                name = "dice",
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
                    executor = CommandExecutor<CommandSource> { inv ->
                        inv.source.reply("rolled ${inv.argv?.get<Long>("count")} dice")
                        1
                    }
                )
            )
        )
        val result = BotCommandRuntime.execute(asCommand(spec), s.source, "dice 3")

        assertEquals(1, result)
        assertEquals(listOf("rolled 3 dice"), s.replies)
    }

    @Test
    fun execute_tree_leaf() {
        val s = fakeSource()
        val spec = CommandDefinition(
            name = "calc",
            description = "calculate",
            root = CommandNodeSpec(
                name = "calc",
                leaf = TreeLeafSpec(
                    arguments = listOf(
                        CommandArgSpec("a", ArgType.IntType, required = true),
                        CommandArgSpec("b", ArgType.IntType, required = true)
                    ),
                    executor = CommandExecutor<CommandSource> { inv ->
                        val a = inv.argv!!.get<Int>("a")
                        val b = inv.argv.get<Int>("b")
                        inv.source.reply("sum = ${a + b}"); 1
                    }
                )
            )
        )
        val result = BotCommandRuntime.execute(
            asCommand(spec),
            s.source,
            "calc 3 5"
        )

        assertEquals(1, result)
        assertEquals(listOf("sum = 8"), s.replies)
    }

    @Test
    fun nested_literal_wins_over_raw() {
        val s = fakeSource()
        val spec = CommandDefinition(
            name = "music",
            description = "music",
            root = CommandNodeSpec(
                name = "music",
                children = listOf(
                    CommandNodeSpec(
                        name = "link",
                        leaf = EmptyLeafSpec(CommandExecutor {
                            it.source.reply("link called")
                            1
                        })
                    )
                ),
                leaf = RawLeafSpec(
                    name = "query",
                    required = true,
                    executor = CommandExecutor<CommandSource> { inv ->
                        inv.source.reply("search: ${inv.rawTail}")
                        1
                    }
                )
            )
        )
        val result = BotCommandRuntime.execute(
            asCommand(spec),
            s.source,
            "music link"
        )

        assertEquals(1, result)
        assertEquals(listOf("link called"), s.replies)
    }

    @Test
    fun child_alias_match() {
        val s = fakeSource()
        val spec = CommandDefinition(
            name = "wiki",
            description = "wiki",
            root = CommandNodeSpec(
                name = "wiki",
                children = listOf(
                    CommandNodeSpec(
                        name = "markdown",
                        aliases = listOf("md"),
                        leaf = EmptyLeafSpec(CommandExecutor<CommandSource> {
                            it.source.reply("md export")
                            1
                        })
                    )
                )
            )
        )
        val result = BotCommandRuntime.execute(
            asCommand(spec),
            s.source,
            "wiki md"
        )

        assertEquals(1, result)
        assertEquals(listOf("md export"), s.replies)
    }

    @Test
    fun unknown_subcommand_returns_parse_error() {
        val s = fakeSource()
        val spec = CommandDefinition(
            name = "wiki",
            description = "wiki",
            root = CommandNodeSpec(
                name = "wiki",
                children = listOf(
                    CommandNodeSpec(
                        name = "md",
                        leaf = EmptyLeafSpec(CommandExecutor<CommandSource> { 1 })
                    )
                )
            )
        )
        val result = BotCommandRuntime.execute(
            asCommand(spec),
            s.source,
            "wiki unknown"
        )

        assertEquals(0, result)
        assertTrue(s.replies[0].contains("未知子命令"))
    }

    @Test
    fun extra_args_on_empty_leaf_returns_parse_error() {
        val s = fakeSource()
        val spec = CommandDefinition(
            name = "ping",
            description = "pong",
            root = CommandNodeSpec(
                name = "ping",
                leaf = EmptyLeafSpec(CommandExecutor<CommandSource> {
                    it.source.reply("pong")
                    1
                })
            )
        )
        val result = BotCommandRuntime.execute(
            asCommand(spec),
            s.source,
            "ping extra args"
        )

        assertEquals(0, result)
        assertTrue(s.replies[0].contains("不需要参数"))
    }

    @Test
    fun missing_subcommand_shows_help_hint() {
        val s = fakeSource()
        val spec = CommandDefinition(
            name = "github",
            description = "GitHub tools",
            root = CommandNodeSpec(
                name = "github",
                children = listOf(
                    CommandNodeSpec(
                        name = "user",
                        leaf = EmptyLeafSpec(CommandExecutor<CommandSource> { 1 })
                    )
                )
            )
        )
        val result = BotCommandRuntime.execute(
            asCommand(spec),
            s.source,
            "github"
        )

        assertEquals(0, result)
        assertTrue(s.replies[0].contains("缺少子命令"))
    }

}
