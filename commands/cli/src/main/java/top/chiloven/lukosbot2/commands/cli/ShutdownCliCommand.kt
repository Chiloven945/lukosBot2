package top.chiloven.lukosbot2.commands.cli

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.ICliCommand
import top.chiloven.lukosbot2.core.IApplicationControl
import top.chiloven.lukosbot2.core.command.definition.dsl.cliCommand

@Service
@ConditionalOnProperty(
    prefix = "lukos.cli.control",
    name = ["shutdown"],
    havingValue = "true",
    matchIfMissing = true
)
class ShutdownCliCommand(
    private val appControl: IApplicationControl
) : ICliCommand {

    override fun definition() = cliCommand("shutdown") {
        alias("stop", "close")
        description = "Shutdown the bot process"

        execute {
            Thread.ofVirtual()
                .name("shutdown-trigger")
                .start { appControl.shutdown() }
        }
    }

}
