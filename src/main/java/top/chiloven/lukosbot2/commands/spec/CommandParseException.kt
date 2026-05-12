package top.chiloven.lukosbot2.commands.spec

class CommandParseException(
    message: String,
    val input: String? = null,
    val cursor: Int? = null
) : RuntimeException(message)
