package top.chiloven.lukosbot2.core.command.runtime

class CommandDispatchException(
    val path: List<String>,
    override val message: String
) : RuntimeException(message)
