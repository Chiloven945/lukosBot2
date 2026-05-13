package top.chiloven.lukosbot2.commands.definition

data class BotCommandSpec(
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String,
    val visible: Boolean = true,
    val root: CommandNodeSpec
)
