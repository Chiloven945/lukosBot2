package top.chiloven.lukosbot2.core.command.definition

/**
 * Specification for a single positional argument.
 *
 * Positional arguments are matched to tokens in order. The last positional
 * may be marked `greedy` to consume all remaining tokens.
 *
 * @param name the argument name used for retrieval and error messages
 * @param type the expected value type
 * @param required whether the argument must be present
 * @param defaultValue fallback value when not supplied (only for non-required)
 * @param greedy if true, consumes all remaining positional tokens
 * @param description human-readable description for usage output
 * @param choices set of allowed string values (validated after type conversion)
 * @param validator optional custom validation callback
 */
data class CommandArg(
    val name: String,
    val type: ArgType,
    val required: Boolean = true,
    val defaultValue: Any? = null,
    val greedy: Boolean = false,
    val description: String = "",
    val choices: List<String> = emptyList(),
    val validator: ValueValidator? = null
)
