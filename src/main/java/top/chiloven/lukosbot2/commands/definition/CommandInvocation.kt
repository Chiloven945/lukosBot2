package top.chiloven.lukosbot2.commands.definition

import top.chiloven.lukosbot2.commands.definition.parser.ArgvParseResult

class CommandInvocation<S>(
    val source: S,
    val rawCommandLine: String,
    val path: List<String>,
    val rawTail: String,
    val argv: ArgvParseResult? = null
) {

    @Suppress("UNCHECKED_CAST")
    fun <T> arg(name: String): T {
        return argv?.get(name)
            ?: throw IllegalArgumentException("missing arg: $name")
    }

}
