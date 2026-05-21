package top.chiloven.lukosbot2.core.command.cli

import top.chiloven.lukosbot2.commands.ICliCommand
import top.chiloven.lukosbot2.core.command.CommandDispatchException
import top.chiloven.lukosbot2.core.command.CommandRuntime

/**
 * CLI-specific wrapper around [CommandRuntime] that catches dispatch
 * errors and formats them in English for console output via `CliCmdContext`.
 *
 * ## Error format
 *
 * ```text
 * CLI command syntax error:
 * <cause>
 *
 * Usage: <path>
 * ```
 */
object CliCommandRuntime {

    /**
     * Executes a CLI command using the given context and raw command line.
     *
     * The first token is matched case-insensitively against the command's
     * name and aliases via [ICliCommand.matches].
     *
     * @param command the CLI command to execute
     * @param source the console context for output
     * @param rawCommandLine the full command line text
     * @return 1 on success, 0 on error (with a message printed to source)
     */
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
