package top.chiloven.lukosbot2.core.command.runtime

import top.chiloven.lukosbot2.cli.ICliCommand
import top.chiloven.lukosbot2.core.cli.CliCmdContext

object CliCommandRuntime {

    fun execute(command: ICliCommand, source: CliCmdContext, rawCommandLine: String): Int {
        val rootToken = CommandRuntime.firstToken(rawCommandLine.trim()) ?: return 0
        if (!command.matches(rootToken)) return 0

        return try {
            CommandRuntime.execute(
                root = command.definition().root,
                rawTail = CommandRuntime.stripFirstToken(rawCommandLine),
                source = source,
                path = listOf(command.name())
            )
        } catch (e: CommandDispatchException) {
            source.printlnErr(
                """
                CLI command syntax error:
                ${e.message}
                
                Usage: ${e.path.joinToString(" ")}
                """.trimIndent()
            )
            0
        }
    }

}
