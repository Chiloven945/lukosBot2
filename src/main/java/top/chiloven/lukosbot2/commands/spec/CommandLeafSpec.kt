package top.chiloven.lukosbot2.commands.spec

sealed interface CommandLeafSpec {

    val executor: CommandExecutor

}

data class TreeLeafSpec(
    val arguments: List<CommandArgSpec>,
    override val executor: CommandExecutor
) : CommandLeafSpec

data class ArgvLeafSpec(
    val positionals: List<CommandArgSpec>,
    val options: List<CommandOptionSpec>,
    override val executor: CommandExecutor
) : CommandLeafSpec

data class RawLeafSpec(
    val name: String,
    val required: Boolean = false,
    override val executor: CommandExecutor
) : CommandLeafSpec

data class EmptyLeafSpec(
    override val executor: CommandExecutor
) : CommandLeafSpec
