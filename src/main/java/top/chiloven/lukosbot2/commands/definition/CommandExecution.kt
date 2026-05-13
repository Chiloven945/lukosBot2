package top.chiloven.lukosbot2.commands.definition

fun interface CommandExecutor {

    fun execute(invocation: CommandInvocation): Int

}

sealed interface CommandResult {

    data object Success : CommandResult

    data class Failure(
        val message: String
    ) : CommandResult

}
