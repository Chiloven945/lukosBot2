package top.chiloven.lukosbot2.core.command.definition.meta

/**
 * Documented parameter entry for the usage tree.
 *
 * Appears in the "Parameters" section of rendered help. Automatically
 * generated from positional argument specs in argv/tree leaves, or can be
 * hand-written to document protocol-specific parameters.
 *
 * @param name the token label (without angle brackets)
 * @param description human-readable explanation
 */
data class CommandParamDoc(
    val name: String,
    val description: String
)
