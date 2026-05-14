package top.chiloven.lukosbot2.commands.definition

data class CommandNodeSpec<S>(
    val name: String,
    val description: String = "",
    val aliases: List<String> = emptyList(),
    val syntaxes: List<CommandSyntaxSpec> = emptyList(),
    val params: List<CommandParamDoc> = emptyList(),
    val options: List<CommandOptionDoc> = emptyList(),
    val examples: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val children: List<CommandNodeSpec<S>> = emptyList(),
    val leaf: CommandLeafSpec<S>? = null
)
