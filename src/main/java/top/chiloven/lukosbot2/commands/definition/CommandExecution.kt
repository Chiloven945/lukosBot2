package top.chiloven.lukosbot2.commands.definition

fun interface CommandExecutor<S> {

    fun execute(invocation: CommandInvocation<S>): Int

}

sealed interface CommandResult {

    data object Success : CommandResult

    data class Failure(
        val message: String
    ) : CommandResult

}
