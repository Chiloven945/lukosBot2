package top.chiloven.lukosbot2.core.command.definition.parser

/**
 * Holds the result of parsing argv-style command input.
 *
 * @param values map of canonicalName/name -> parsed value for all options and positionals
 * @param positionals the raw string tokens that were treated as positional arguments
 * @param unknownOptions reserved for future use (currently always empty)
 */
data class ArgvParseResult(
    val values: Map<String, Any?>,
    val positionals: List<String>,
    val unknownOptions: List<String> = emptyList()
) {

    /**
     * Retrieves a typed value by name. Throws if not present.
     *
     * @param name the argument name as declared in the DSL
     * @return the typed value
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String): T = values[name] as T

    /**
     * Retrieves a typed value by name, or null if not present.
     *
     * @param name the argument name
     * @return the typed value, or null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrNull(name: String): T? = values[name] as? T

}
