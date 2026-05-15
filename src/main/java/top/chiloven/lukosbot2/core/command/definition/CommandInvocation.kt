package top.chiloven.lukosbot2.core.command.definition

import top.chiloven.lukosbot2.core.command.definition.parser.ArgvParseResult

/**
 * Execution context for a single command invocation.
 *
 * This object is constructed by the runtime and passed to a
 * [CommandExecutor]. It carries:
 *
 *   - The typed `source` for output (e.g. `CommandSource` for bot,
 *     `CliCmdContext` for CLI)
 *   - The reconstructed raw command line
 *   - The matched literal path (e.g. `["github", "search"]`)
 *   - The raw tail text after the last matched literal
 *   - Parsed argv values (if applicable)
 *
 * Use [arg] to retrieve named positional/option values,
 * or access the [argv] result directly for advanced cases.
 *
 * @param S the source type
 * @param source the output target for replies or console output
 * @param rawCommandLine the full command text after the prefix
 * @param path ordered list of literal names matched by the runtime
 * @param rawTail text remaining after all literal matching
 * @param argv parsed argument values (null for empty/raw leaves)
 */
class CommandInvocation<S>(
    val source: S,
    val rawCommandLine: String,
    val path: List<String>,
    val rawTail: String,
    val argv: ArgvParseResult? = null
) {

    var resultCode: Int = 1

    fun code(): Int = resultCode

    /**
     * Retrieves a named value from the parsed argv result.
     *
     * @param name the argument name as declared in the DSL
     * @return the typed value
     * @throws IllegalArgumentException if the name is not present
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> arg(name: String): T {
        return argv?.get(name)
            ?: throw IllegalArgumentException("missing arg: $name")
    }

}
