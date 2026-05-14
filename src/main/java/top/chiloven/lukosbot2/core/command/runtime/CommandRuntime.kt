package top.chiloven.lukosbot2.core.command.runtime

import top.chiloven.lukosbot2.commands.definition.*
import top.chiloven.lukosbot2.commands.definition.parser.ArgvParser
import top.chiloven.lukosbot2.commands.definition.parser.ShellWords

object CommandRuntime {

    fun <S> execute(
        root: CommandNodeSpec<S>,
        rawTail: String,
        source: S,
        path: List<String> = emptyList()
    ): Int {
        val tail = rawTail.trimStart()
        val firstToken = firstToken(tail)

        if (firstToken != null) {
            val matched = matchChildLiteral(root.children, firstToken)
            if (matched != null) {
                return execute(
                    root = matched,
                    rawTail = stripFirstToken(tail),
                    source = source,
                    path = path + matched.name
                )
            }
        }

        val leaf = root.leaf
            ?: if (firstToken != null) {
                throw CommandDispatchException(path, "未知子命令：$firstToken")
            } else {
                throw CommandDispatchException(path, "缺少子命令")
            }

        return executeLeaf(leaf, tail, source, path)
    }

    private fun <S> executeLeaf(
        leaf: CommandLeafSpec<S>,
        rawTail: String,
        source: S,
        path: List<String>
    ): Int {
        return try {
            when (leaf) {
                is EmptyLeafSpec -> {
                    if (rawTail.isNotEmpty()) throw CommandDispatchException(path, "不需要参数，但收到了：$rawTail")
                    val inv = CommandInvocation(source, path.joinToString(" "), path, "")
                    leaf.executor.execute(inv)
                }

                is RawLeafSpec -> {
                    if (leaf.required && rawTail.isBlank()) throw CommandDispatchException(
                        path,
                        "缺少必填参数：${leaf.name}"
                    )
                    val fullLine = if (rawTail.isNotEmpty()) "${path.joinToString(" ")} $rawTail" else path.joinToString(" ")
                    val inv = CommandInvocation(source, fullLine, path, rawTail)
                    leaf.executor.execute(inv)
                }

                is ArgvLeafSpec -> {
                    val tokens = ShellWords.split(rawTail)
                    val result = ArgvParser.parse(tokens, leaf.positionals, leaf.options)
                    val fullLine =
                        if (rawTail.isNotEmpty()) "${path.joinToString(" ")} $rawTail" else path.joinToString(" ")
                    val inv = CommandInvocation(source, fullLine, path, rawTail, argv = result)
                    leaf.executor.execute(inv)
                }

                is TreeLeafSpec -> {
                    val tokens = ShellWords.split(rawTail)
                    val result = ArgvParser.parse(tokens, leaf.arguments, emptyList())
                    val fullLine =
                        if (rawTail.isNotEmpty()) "${path.joinToString(" ")} $rawTail" else path.joinToString(" ")
                    val inv = CommandInvocation(source, fullLine, path, rawTail, argv = result)
                    leaf.executor.execute(inv)
                }
            }
        } catch (e: CommandParseException) {
            throw CommandDispatchException(path, e.message ?: "参数错误")
        } catch (e: IllegalArgumentException) {
            throw CommandDispatchException(path, e.message ?: "参数错误")
        }
    }

    internal fun matchChildLiteral(
        children: List<CommandNodeSpec<*>>,
        token: String
    ): CommandNodeSpec<*>? {
        for (child in children) {
            if (child.name.equals(token, ignoreCase = true)) return child
            if (child.aliases.any { it.equals(token, ignoreCase = true) }) return child
        }
        return null
    }

    internal fun firstToken(input: String): String? {
        val trimmed = input.trimStart()
        if (trimmed.isEmpty()) return null
        val spaceIdx = trimmed.indexOf(' ')
        return if (spaceIdx < 0) trimmed else trimmed.substring(0, spaceIdx)
    }

    internal fun stripFirstToken(input: String): String {
        val trimmed = input.trimStart()
        val spaceIdx = trimmed.indexOf(' ')
        return if (spaceIdx < 0) "" else trimmed.substring(spaceIdx + 1).trimStart()
    }

}
