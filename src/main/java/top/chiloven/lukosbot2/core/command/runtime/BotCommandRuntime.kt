package top.chiloven.lukosbot2.core.command.runtime

import top.chiloven.lukosbot2.commands.definition.BotCommandSpec
import top.chiloven.lukosbot2.core.command.CommandSource

object BotCommandRuntime {

    fun execute(
        definition: BotCommandSpec,
        source: CommandSource,
        rawCommandLine: String
    ): Int {
        val trimmed = rawCommandLine.trim()
        val rootToken = CommandRuntime.firstToken(trimmed) ?: trimmed

        if (!rootToken.equals(definition.name, ignoreCase = true)) {
            return 0
        }

        val rawTail = CommandRuntime.stripFirstToken(trimmed)
        val path = listOf(definition.name)

        return CommandRuntime.execute(
            root = definition.root,
            rawTail = rawTail,
            source = source,
            path = path
        )
    }

}
