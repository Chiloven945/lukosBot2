package top.chiloven.lukosbot2.core.command.definition.leaf

import top.chiloven.lukosbot2.core.command.definition.CommandArg
import top.chiloven.lukosbot2.core.command.definition.CommandExecutor
import top.chiloven.lukosbot2.core.command.definition.CommandOption

/**
 * Leaf that tokenizes the tail via `ShellWords` and parses into named
 * positional arguments and options using `ArgvParser`.
 *
 * This is the most common leaf type for commands with structured arguments.
 * Supports `--key=value`, `-f`, boolean flags, split lists,
 * repeatable options, greedy positionals, and more.
 *
 * @param positionals ordered list of positional argument specs
 * @param options list of option/flag specs
 */
data class ArgvLeaf<S>(
    val positionals: List<CommandArg>,
    val options: List<CommandOption>,
    override val executor: CommandExecutor<S>
) : CommandLeaf<S>
