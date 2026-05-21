package top.chiloven.lukosbot2.core.command.definition

/**
 * Functional interface representing the executable logic of a command leaf.
 *
 * Receives a [CommandInvocation] carrying the source, parsed arguments,
 * and raw command text. Returns an integer status code:
 *
 *   - `1` — success
 *   - `0` — argument error or business failure
 *
 *
 * @param S the source type
 */
fun interface CommandExecutor<S> {

    /**
     * Executes the command with the given invocation context.
     *
     * @param invocation the populated invocation context
     * @return 1 for success, 0 for failure
     */
    fun execute(invocation: CommandInvocation<S>): Int

}
