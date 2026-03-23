package top.chiloven.lukosbot2.cli.impl

import com.mojang.brigadier.CommandDispatcher
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Main
import top.chiloven.lukosbot2.cli.ICliCommand
import top.chiloven.lukosbot2.core.cli.CliCmdContext
import top.chiloven.lukosbot2.util.brigadier.builder.CliLAB.literal

@Service
@ConditionalOnProperty(
    prefix = "lukos.cli.control",
    name = ["shutdown"],
    havingValue = "true",
    matchIfMissing = true
)
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
