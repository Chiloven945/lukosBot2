package top.chiloven.lukosbot2.core.command.definition

/**
 * Validates a parsed value and returns an error message, or `null` for success.
 *
 * Used in the DSL via the `validate { ... ` otherwise "..."} pattern.
 */
fun interface ValueValidator {

    /**
     * Validates a value and returns an error string, or null if valid.
     *
     * @param value the parsed value to check
     * @return error message, or null
     */
    fun validate(value: Any?): String?

}
