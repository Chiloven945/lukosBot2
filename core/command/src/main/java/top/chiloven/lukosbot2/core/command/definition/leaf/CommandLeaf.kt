package top.chiloven.lukosbot2.core.command.definition.leaf

import top.chiloven.lukosbot2.core.command.definition.CommandExecutor

/**
 * Sealed interface for command leaf execution strategies.
 *
 * A leaf is reached by the runtime when no child literal matches the remaining
 * input at the current path position. The runtime then dispatches to the leaf's
 * executor according to the leaf type's strategy:
 *
 *   - [EmptyLeaf] — no arguments expected; raw tail must be empty
 *   - [RawLeaf] — passes the entire raw tail as a single string
 *   - [ArgvLeaf] — tokenizes the tail via `ShellWords` and
 *     parses with `ArgvParser` into named positional and option values
 *   - [TreeLeaf] — parses the tail as a list of positional arguments
 *
 * @param S the source type
 */
sealed interface CommandLeaf<S> {

    /**
     * The callable that processes a [top.chiloven.lukosbot2.core.command.definition.CommandInvocation]
     */
    val executor: CommandExecutor<S>

}
