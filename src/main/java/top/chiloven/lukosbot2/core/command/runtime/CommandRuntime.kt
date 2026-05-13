package top.chiloven.lukosbot2.core.command.runtime

import top.chiloven.lukosbot2.commands.definition.*
import top.chiloven.lukosbot2.commands.definition.parser.ArgvParser
import top.chiloven.lukosbot2.commands.definition.parser.ShellWords
import top.chiloven.lukosbot2.core.command.CommandSource

object CommandRuntime {

    fun execute(
        root: CommandNodeSpec,
        rawTail: String,
        source: CommandSource,
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
        if (leaf == null) {
            if (firstToken != null) {
                source.reply("未知子命令：$firstToken\n\n发送 /help ${path.joinToString(" ")} 查看详细用法。")
            } else {
                source.reply("缺少子命令。\n\n发送 /help ${path.joinToString(" ")} 查看详细用法。")
            }
            return 0
        }

        return executeLeaf(leaf, tail, source, path)
    }

    private fun executeLeaf(
        leaf: CommandLeafSpec,
        rawTail: String,
        source: CommandSource,
        path: List<String>
    ): Int {
        return try {
            when (leaf) {
                is EmptyLeafSpec -> executeEmptyLeaf(leaf, rawTail, source, path)
                is RawLeafSpec -> executeRawLeaf(leaf, rawTail, source, path)
                is ArgvLeafSpec -> executeArgvLeaf(leaf, rawTail, source, path)
                is TreeLeafSpec -> executeTreeLeaf(leaf, rawTail, source, path)
            }
        } catch (e: CommandParseException) {
            source.reply(formatError(e.message ?: "参数错误", path))
            0
        } catch (e: IllegalArgumentException) {
            source.reply(formatError(e.message ?: "参数错误", path))
            0
        }
    }

    private fun executeEmptyLeaf(
        leaf: EmptyLeafSpec,
        rawTail: String,
        source: CommandSource,
        path: List<String>
    ): Int {
        if (rawTail.isNotEmpty()) {
            source.reply(formatError("不需要参数，但收到了：$rawTail", path))
            return 0
        }
        val inv = CommandInvocation(
            source = source,
            rawCommandLine = path.joinToString(" "),
            path = path,
            rawTail = ""
        )
        return leaf.executor.execute(inv)
    }

    private fun executeRawLeaf(
        leaf: RawLeafSpec,
        rawTail: String,
        source: CommandSource,
        path: List<String>
    ): Int {
        if (leaf.required && rawTail.isBlank()) {
            source.reply(formatError("缺少必填参数：${leaf.name}", path))
            return 0
        }
        val fullLine = if (rawTail.isNotEmpty()) {
            "${path.joinToString(" ")} $rawTail"
        } else {
            path.joinToString(" ")
        }
        val inv = CommandInvocation(
            source = source,
            rawCommandLine = fullLine,
            path = path,
            rawTail = rawTail
        )
        return leaf.executor.execute(inv)
    }

    private fun executeArgvLeaf(
        leaf: ArgvLeafSpec,
        rawTail: String,
        source: CommandSource,
        path: List<String>
    ): Int {
        val tokens = ShellWords.split(rawTail)
        val result = ArgvParser.parse(tokens, leaf.positionals, leaf.options)
        val fullLine = if (rawTail.isNotEmpty()) {
            "${path.joinToString(" ")} $rawTail"
        } else {
            path.joinToString(" ")
        }
        val inv = CommandInvocation(
            source = source,
            rawCommandLine = fullLine,
            path = path,
            rawTail = rawTail,
            argv = result
        )
        return leaf.executor.execute(inv)
    }

    private fun executeTreeLeaf(
        leaf: TreeLeafSpec,
        rawTail: String,
        source: CommandSource,
        path: List<String>
    ): Int {
        val tokens = ShellWords.split(rawTail)
        val result = ArgvParser.parse(tokens, leaf.arguments, emptyList())
        val fullLine = if (rawTail.isNotEmpty()) {
            "${path.joinToString(" ")} $rawTail"
        } else {
            path.joinToString(" ")
        }
        val inv = CommandInvocation(
            source = source,
            rawCommandLine = fullLine,
            path = path,
            rawTail = rawTail,
            argv = result
        )
        return leaf.executor.execute(inv)
    }

    internal fun matchChildLiteral(
        children: List<CommandNodeSpec>,
        token: String
    ): CommandNodeSpec? {
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

    private fun formatError(cause: String, path: List<String>): String {
        val cmdPath = path.joinToString(" ")
        return "命令参数错误：\n$cause\n\n发送 /help $cmdPath 查看详细用法。"
    }

}
