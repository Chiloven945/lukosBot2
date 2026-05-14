package top.chiloven.lukosbot2.commands.definition

data class CommandDefinition<S>(
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String,
    val visible: Boolean = true,
    val root: CommandNodeSpec<S>
)
