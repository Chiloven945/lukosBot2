package top.chiloven.lukosbot2.commands.impl

import com.mojang.brigadier.CommandDispatcher
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.CommandSource

@Service
class CaveCommand : IBotCommand {
    override fun name(): String = "cave"

    override fun aliases(): List<String> = listOf("c")

    override fun description(): String = "回声洞！"

    override fun usage(): UsageNode =
        UsageNode.root(name())
            .description(description())
            .alias(aliases())
            .build()

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {

    }

}
