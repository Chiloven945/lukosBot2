package top.chiloven.lukosbot2.core.command.definition.leaf

import top.chiloven.lukosbot2.core.command.definition.CommandArg
import top.chiloven.lukosbot2.core.command.definition.CommandExecutor

/**
 * Leaf that parses the tail as a list of positional arguments without options.
 *
 * Each token is matched to an argument spec in order. The last argument may
 * be greedy (consuming all remaining tokens). Used for simple commands like
 * `/calc <a> <b>`.
 *
 * @param arguments ordered list of argument specs (no options)
 */
data class TreeLeaf<S>(
    val arguments: List<CommandArg>,
    override val executor: CommandExecutor<S>
) : CommandLeaf<S>
