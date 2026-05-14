package top.chiloven.lukosbot2.core.command.definition.meta

/**
 * Documented option/flag entry for the usage tree.
 *
 * Appears in the "Options" section of rendered help. Automatically
 * generated from option specs in argv leaves, or can be handwritten.
 *
 * @param name the option token (e.g. `"-t"`, `"--verbose"`)
 * @param description human-readable explanation
 */
data class CommandOptionDoc(
    val name: String,
    val description: String
)
