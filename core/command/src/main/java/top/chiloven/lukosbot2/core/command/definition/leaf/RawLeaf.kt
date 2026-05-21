package top.chiloven.lukosbot2.core.command.definition.leaf

import top.chiloven.lukosbot2.core.command.definition.CommandExecutor

/**
 * Leaf that passes the entire remaining input as a raw string.
 *
 * If `required` is `true` and the raw tail is blank, the runtime
 * returns a "missing required argument" error. Used for commands like
 * `/echo <text>`.
 *
 * @param name the argument name shown in usage and error messages
 * @param required whether the argument must be non-blank
 */
data class RawLeaf<S>(
    val name: String,
    val required: Boolean = false,
    override val executor: CommandExecutor<S>
) : CommandLeaf<S>
