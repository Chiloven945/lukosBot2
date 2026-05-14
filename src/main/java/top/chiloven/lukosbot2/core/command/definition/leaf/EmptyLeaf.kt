package top.chiloven.lukosbot2.core.command.definition.leaf

import top.chiloven.lukosbot2.core.command.definition.CommandExecutor

/**
 * Leaf that expects no arguments.
 *
 * The raw tail must be empty; otherwise the runtime returns a parse error.
 * Used for commands like `/ping` or `/start`.
 */
data class EmptyLeaf<S>(
    override val executor: CommandExecutor<S>
) : CommandLeaf<S>
