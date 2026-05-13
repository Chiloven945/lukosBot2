package top.chiloven.lukosbot2.commands.definition.bridge

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import top.chiloven.lukosbot2.commands.definition.*
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB
import top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB

object SpecBrigadierRegistrar {

    fun register(
        dispatcher: CommandDispatcher<CommandSource>,
        spec: BotCommandSpec
    ) {
        val rootNode = buildNode(spec.root, emptyList())
        dispatcher.register(rootNode)
    }

    private fun buildNode(
        node: CommandNodeSpec,
        parentPath: List<String>
    ): LiteralArgumentBuilder<CommandSource> {
        val builder = CommandLAB.literal(node.name)
        val path = parentPath + node.name
        val leaf = node.leaf

        if (leaf != null) {
            when (leaf) {
                is EmptyLeafSpec -> {
                    builder.executes(
                        SpecExecutionBridge.emptyCommand(leaf.executor, path)
                    )
                }
                is RawLeafSpec -> {
                    if (!leaf.required) {
                        builder.executes(
                            SpecExecutionBridge.rawEmptyCommand(leaf.executor, path)
                        )
                    }
                    builder.then(
                        CommandRAB.argument(leaf.name, StringArgumentType.greedyString())
                            .executes(
                                SpecExecutionBridge.rawCommand(leaf.executor, path, leaf.name)
                            )
                    )
                }
                is ArgvLeafSpec -> {
                    if (leaf.positionals.all { !it.required }) {
                        builder.executes(
                            SpecExecutionBridge.argvEmptyCommand(
                                leaf.executor,
                                leaf.positionals,
                                leaf.options,
                                path
                            )
                        )
                    }
                    builder.then(
                        CommandRAB.argument("__args", StringArgumentType.greedyString())
                            .executes(
                                SpecExecutionBridge.argvCommand(
                                    leaf.executor,
                                    leaf.positionals,
                                    leaf.options,
                                    path
                                )
                            )
                    )
                }
                is TreeLeafSpec -> {
                    builder.then(
                        CommandRAB.argument("__args", StringArgumentType.greedyString())
                            .executes(
                                SpecExecutionBridge.treeCommand(
                                    leaf.executor,
                                    leaf.arguments,
                                    path
                                )
                            )
                    )
                }
            }
        }

        for (child in node.children) {
            builder.then(buildNode(child, path))
        }

        return builder
    }
}
