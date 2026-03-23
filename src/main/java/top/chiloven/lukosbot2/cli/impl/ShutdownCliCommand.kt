package top.chiloven.lukosbot2.cli.impl

import com.mojang.brigadier.CommandDispatcher
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Main
import top.chiloven.lukosbot2.cli.ICliCommand
import top.chiloven.lukosbot2.core.cli.CliCmdContext
import top.chiloven.lukosbot2.util.brigadier.builder.CliLAB.literal

@Service
class ShutdownCliCommand : ICliCommand {

    override fun name(): String = "shutdown"

    override fun description(): String = "Shutdown the bot process"

    override fun usage(): String = "shutdown"

    override fun register(dispatcher: CommandDispatcher<CliCmdContext>) {
        dispatcher.register(
            literal(name())
                .executes {
                    Thread.ofVirtual().name("shutdown-trigger").start {
                        Main.shutdown()
                    }
                    1
                }
        )
    }

}
