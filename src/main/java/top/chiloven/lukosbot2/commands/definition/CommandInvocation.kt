package top.chiloven.lukosbot2.commands.definition

import top.chiloven.lukosbot2.commands.definition.parser.ArgvParseResult
import top.chiloven.lukosbot2.core.command.CommandSource

class CommandInvocation(
    val source: CommandSource,
    val rawCommandLine: String,
    val path: List<String>,
    val rawTail: String,
    val argv: ArgvParseResult? = null
) {

    fun reply(text: String) = source.reply(text)

    @Suppress("UNCHECKED_CAST")
    fun <T> arg(name: String): T {
        return argv?.get(name)
            ?: throw IllegalArgumentException("missing arg: $name")
    }

}
