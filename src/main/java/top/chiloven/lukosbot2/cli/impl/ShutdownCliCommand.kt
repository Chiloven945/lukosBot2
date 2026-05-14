package top.chiloven.lukosbot2.cli.impl

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Main
import top.chiloven.lukosbot2.cli.ICliCommand
import top.chiloven.lukosbot2.commands.definition.dsl.cliCommand

@Service
@ConditionalOnProperty(
    prefix = "lukos.cli.control",
    name = ["shutdown"],
    havingValue = "true",
    matchIfMissing = true
)
class ShutdownCliCommand : ICliCommand {

    override fun definition() = cliCommand("shutdown") {
        alias("stop", "close")
        description = "Shutdown the bot process"
        execute {
            Thread.ofVirtual()
                .name("shutdown-trigger")
                .start { Main.shutdown() }
        }
    }

    override fun aliases() = listOf("stop", "close")

}
