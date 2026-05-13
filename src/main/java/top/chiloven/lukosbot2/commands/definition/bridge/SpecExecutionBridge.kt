package top.chiloven.lukosbot2.commands.definition.bridge

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import top.chiloven.lukosbot2.commands.definition.*
import top.chiloven.lukosbot2.commands.definition.parser.ArgvParser
import top.chiloven.lukosbot2.commands.definition.parser.ShellWords
import top.chiloven.lukosbot2.core.command.CommandSource

object SpecExecutionBridge {

    fun emptyCommand(
        executor: CommandExecutor,
        path: List<String>
    ): Command<CommandSource> = Command<CommandSource> { ctx ->
        val inv = CommandInvocation(
            source = ctx.source,
            rawCommandLine = ctx.input,
            path = path,
            rawTail = ""
        )
        safeExecute(ctx.source, inv, executor)
    }

    fun rawCommand(
        executor: CommandExecutor,
        path: List<String>,
        argName: String
    ): Command<CommandSource> = Command<CommandSource> { ctx ->
        val rawTail = StringArgumentType.getString(ctx, argName)
        val inv = CommandInvocation(
            source = ctx.source,
            rawCommandLine = ctx.input,
            path = path,
            rawTail = rawTail
        )
        safeExecute(ctx.source, inv, executor)
    }

    fun rawEmptyCommand(
        executor: CommandExecutor,
        path: List<String>
    ): Command<CommandSource> = Command<CommandSource> { ctx ->
        val inv = CommandInvocation(
            source = ctx.source,
            rawCommandLine = ctx.input,
            path = path,
            rawTail = ""
        )
        safeExecute(ctx.source, inv, executor)
    }

    fun argvCommand(
        executor: CommandExecutor,
        positionals: List<CommandArgSpec>,
        options: List<CommandOptionSpec>,
        path: List<String>
    ): Command<CommandSource> = Command<CommandSource> { ctx ->
        val rawTail = getArgvTail(ctx)
        val tokens = ShellWords.split(rawTail)
        val result = ArgvParser.parse(tokens, positionals, options)
        val inv = CommandInvocation(
            source = ctx.source,
            rawCommandLine = ctx.input,
            path = path,
            rawTail = rawTail,
            argv = result
        )
        safeExecute(ctx.source, inv, executor)
    }

    fun argvEmptyCommand(
        executor: CommandExecutor,
        positionals: List<CommandArgSpec>,
        options: List<CommandOptionSpec>,
        path: List<String>
    ): Command<CommandSource> = Command<CommandSource> { ctx ->
        val result = ArgvParser.parse(emptyList(), positionals, options)
        val inv = CommandInvocation(
            source = ctx.source,
            rawCommandLine = ctx.input,
            path = path,
            rawTail = "",
            argv = result
        )
        safeExecute(ctx.source, inv, executor)
    }

    fun treeCommand(
        executor: CommandExecutor,
        arguments: List<CommandArgSpec>,
        path: List<String>
    ): Command<CommandSource> = Command<CommandSource> { ctx ->
        val rawTail = getArgvTail(ctx)
        val tokens = ShellWords.split(rawTail)
        val result = ArgvParser.parse(tokens, arguments, emptyList())
        val inv = CommandInvocation(
            source = ctx.source,
            rawCommandLine = ctx.input,
            path = path,
            rawTail = rawTail,
            argv = result
        )
        safeExecute(ctx.source, inv, executor)
    }

    private fun getArgvTail(ctx: CommandContext<CommandSource>): String {
        return try {
            StringArgumentType.getString(ctx, "__args")
        } catch (_: IllegalArgumentException) {
            ""
        }
    }

    private fun safeExecute(
        src: CommandSource,
        inv: CommandInvocation,
        executor: CommandExecutor
    ): Int {
        return try {
            executor.execute(inv)
        } catch (e: CommandParseException) {
            src.reply("命令参数错误：\n${e.message}")
            0
        } catch (e: IllegalArgumentException) {
            src.reply(e.message ?: "参数错误")
            0
        }
    }
}
