package top.chiloven.lukosbot2.commands.spec

data class CommandOptionSpec(
    val canonicalName: String,
    val names: List<String>,
    val type: ArgType,
    val required: Boolean = false,
    val defaultValue: Any? = null,
    val repeatable: Boolean = false,
    val splitBy: String? = null,
    val description: String = "",
    val choices: List<String> = emptyList(),
    val validator: ValueValidator? = null
)
