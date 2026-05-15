package top.chiloven.lukosbot2.core.command.definition

/**
 * Exception thrown when command input fails to parse according to the command definition.
 *
 * Typically thrown by the parser layer (`ArgvParser`, `ShellWords`)
 * and caught by the runtime to produce end-user-facing error messages.
 *
 * @param message human-readable error cause
 * @param input the raw input that caused the error (optional)
 * @param cursor the character position of the error (optional)
 */
class CommandParseException(
    message: String,
    val input: String? = null,
    val cursor: Int? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
