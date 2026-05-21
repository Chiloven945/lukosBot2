package top.chiloven.lukosbot2.core.command.definition

/**
 * Describes a single syntax line for the command usage tree.
 *
 * Each syntax line is a pattern showing how the command may be invoked.
 * For example, `/ip <ip> [--provider=<name>]` is one syntax line.
 *
 * @param description optional per-syntax annotation
 * @param items the structured syntax items forming this line
 */
data class CommandSyntax(
    val description: String = "",
    val items: List<SyntaxItem> = emptyList()
)
