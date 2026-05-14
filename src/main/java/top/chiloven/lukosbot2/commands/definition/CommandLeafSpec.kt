package top.chiloven.lukosbot2.commands.definition

sealed interface CommandLeafSpec<S> {

    val executor: CommandExecutor<S>

}

data class TreeLeafSpec<S>(
    val arguments: List<CommandArgSpec>,
    override val executor: CommandExecutor<S>
) : CommandLeafSpec<S>

data class ArgvLeafSpec<S>(
    val positionals: List<CommandArgSpec>,
    val options: List<CommandOptionSpec>,
    override val executor: CommandExecutor<S>
) : CommandLeafSpec<S>

data class RawLeafSpec<S>(
    val name: String,
    val required: Boolean = false,
    override val executor: CommandExecutor<S>
) : CommandLeafSpec<S>

data class EmptyLeafSpec<S>(
    override val executor: CommandExecutor<S>
) : CommandLeafSpec<S>
