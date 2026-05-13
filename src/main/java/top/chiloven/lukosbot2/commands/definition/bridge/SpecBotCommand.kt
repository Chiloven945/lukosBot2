package top.chiloven.lukosbot2.commands.definition.bridge

import com.mojang.brigadier.CommandDispatcher
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.commands.definition.BotCommandSpec
import top.chiloven.lukosbot2.core.command.CommandSource

abstract class SpecBotCommand : IBotCommand {

    private val lazySpec: BotCommandSpec by lazy { spec() }

    protected abstract fun spec(): BotCommandSpec

    final override fun name(): String = lazySpec.name

    final override fun aliases(): List<String> = lazySpec.aliases

    final override fun description(): String = lazySpec.description

    final override fun isVisible(): Boolean = lazySpec.visible

    final override fun usage(): UsageNode =
        CommandUsageMapper.toUsageNode(lazySpec)

    final override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        SpecBrigadierRegistrar.register(dispatcher, lazySpec)
    }

}
