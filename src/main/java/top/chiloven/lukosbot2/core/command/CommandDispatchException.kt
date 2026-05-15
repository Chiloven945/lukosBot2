package top.chiloven.lukosbot2.core.command

/**
 * Thrown by [CommandRuntime] when the parsed input does not match
 * the command definition — for example, unknown subcommands, missing required
 * arguments, or unexpected extra arguments.
 *
 * Wrappers ([top.chiloven.lukosbot2.core.command.bot.BotCommandRuntime], [top.chiloven.lukosbot2.core.command.cli.CliCommandRuntime]) catch this
 * exception and format the message according to their platform conventions.
 *
 * @param path the literal path matched so far (e.g. `["github", "search"]`)
 * @param message the human-readable error cause
 */
class CommandDispatchException(
    val path: List<String>,
    override val message: String
) : RuntimeException(message)
