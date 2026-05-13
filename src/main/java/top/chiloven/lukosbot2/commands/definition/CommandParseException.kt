package top.chiloven.lukosbot2.commands.definition

class CommandParseException(
    message: String,
    val input: String? = null,
    val cursor: Int? = null
) : RuntimeException(message)
