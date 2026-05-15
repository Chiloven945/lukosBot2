package top.chiloven.lukosbot2.core.command

import top.chiloven.lukosbot2.core.command.definition.CommandInvocation
import top.chiloven.lukosbot2.core.command.definition.CommandNode
import top.chiloven.lukosbot2.core.command.definition.CommandParseException
import top.chiloven.lukosbot2.core.command.definition.leaf.*
import top.chiloven.lukosbot2.core.command.definition.parser.ArgvParser
import top.chiloven.lukosbot2.core.command.definition.parser.ShellWords

/**
 * Source-type-agnostic command execution engine.
 *
 * This singleton handles the core dispatch logic:
 *
 * 1. Tokenize the raw command tail to find the first literal token
 * 2. Recursively match child literal nodes (case-insensitive, aliases included)
 * 3. If no child matches, execute the current node's leaf
 * 4. If no leaf exists, throw [CommandDispatchException]
 *
 * `CommandRuntime` does **not** format error messages or interact
 * with the source directly. Instead, it throws [CommandDispatchException]
 * for structured errors and [top.chiloven.lukosbot2.core.command.definition.CommandParseException] / `IllegalArgumentException`
 * from the parser, which are caught by type-specific wrappers
 * ([top.chiloven.lukosbot2.core.command.bot.BotCommandRuntime], [top.chiloven.lukosbot2.core.command.cli.CliCommandRuntime]) and formatted according
 * to platform conventions.
 *
 * ## Literal matching priority
 *
 * Literal children are always checked before falling back to a leaf.
 * This means `/music link` matches the `"link"` subcommand rather than
 * treating `"link"` as a raw search query.
 */
object CommandRuntime {

    /**
     * Recursively matches literal children and executes the resolved leaf.
     *
     * @param S the source type
     * @param root the current node in the definition tree
     * @param rawTail the remaining unparsed command text
     * @param source the typed output target
     * @param path the literal path matched so far
     * @return the executor's return code (1 = success, 0 = failure)
     * @throws CommandDispatchException on structure errors
     * @throws top.chiloven.lukosbot2.core.command.definition.CommandParseException on argument parsing errors
     */
    fun <S> execute(
        root: CommandNode<S>,
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
        leaf: CommandLeaf<S>,
        rawTail: String,
        source: S,
        path: List<String>
    ): Int {
        return try {
            when (leaf) {
                is EmptyLeaf -> {
                    if (rawTail.isNotEmpty()) throw CommandDispatchException(
                        path,
                        "不需要参数，但收到了：$rawTail"
                    )

                    val inv = CommandInvocation(
                        source,
                        path.joinToString(" "),
                        path,
                        ""
                    )
                    leaf.executor.execute(inv)
                }

                is RawLeaf -> {
                    if (leaf.required && rawTail.isBlank()) throw CommandDispatchException(
                        path,
                        "缺少必填参数：${leaf.name}"
                    )

                    val fullLine = if (rawTail.isNotEmpty()) {
                        "${path.joinToString(" ")} $rawTail"
                    } else {
                        path.joinToString(" ")
                    }
                    val inv = CommandInvocation(
                        source,
                        fullLine,
                        path,
                        rawTail
                    )
                    leaf.executor.execute(inv)
                }

                is ArgvLeaf -> {
                    val tokens = ShellWords.split(rawTail)
                    val result = ArgvParser.parse(tokens, leaf.positionals, leaf.options)
                    val fullLine = if (rawTail.isNotEmpty()) {
                        "${path.joinToString(" ")} $rawTail"
                    } else {
                        path.joinToString(" ")
                    }
                    val inv = CommandInvocation(
                        source,
                        fullLine,
                        path,
                        rawTail,
                        argv = result
                    )
                    leaf.executor.execute(inv)
                }

                is TreeLeaf -> {
                    val tokens = ShellWords.split(rawTail)
                    val result = ArgvParser.parse(tokens, leaf.arguments, emptyList())
                    val fullLine = if (rawTail.isNotEmpty()) {
                        "${path.joinToString(" ")} $rawTail"
                    } else {
                        path.joinToString(" ")
                    }
                    val inv = CommandInvocation(
                        source,
                        fullLine,
                        path,
                        rawTail,
                        argv = result
                    )
                    leaf.executor.execute(inv)
                }
            }
        } catch (e: CommandParseException) {
            throw CommandDispatchException(path, e.message ?: "参数错误")
        } catch (e: IllegalArgumentException) {
            throw CommandDispatchException(path, e.message ?: "参数错误")
        }
    }

    /**
     * Finds a child literal node matching the given token.
     *
     * Matching is case-insensitive and includes node-specific aliases.
     *
     * @return the matching child node, or `null` if no match
     */
    internal fun <S> matchChildLiteral(
        children: List<CommandNode<S>>,
        token: String
    ): CommandNode<S>? {
        for (child in children) {
            if (child.name.equals(token, ignoreCase = true)) return child
            if (child.aliases.any { it.equals(token, ignoreCase = true) }) return child
        }
        return null
    }

    /**
     * Extracts the first whitespace-delimited token from `input`.
     *
     * @return the first token, or `null` if the input is blank
     */
    internal fun firstToken(input: String): String? {
        val trimmed = input.trimStart()
        if (trimmed.isEmpty()) return null
        val wsIdx = trimmed.indexOfFirst { it.isWhitespace() }
        return if (wsIdx < 0) trimmed else trimmed.substring(0, wsIdx)
    }

    /**
     * Removes the first whitespace-delimited token from `input`.
     *
     * @return the input with the first token stripped, or empty string
     */
    internal fun stripFirstToken(input: String): String {
        val trimmed = input.trimStart()
        val wsIdx = trimmed.indexOfFirst { it.isWhitespace() }
        return if (wsIdx < 0) "" else trimmed.substring(wsIdx + 1).trimStart()
    }

}
