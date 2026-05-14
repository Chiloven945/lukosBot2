package top.chiloven.lukosbot2.cli.impl

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.cli.ICliCommand
import top.chiloven.lukosbot2.commands.definition.ArgType
import top.chiloven.lukosbot2.commands.definition.dsl.cliCommand
import top.chiloven.lukosbot2.core.MessageSenderHub
import top.chiloven.lukosbot2.model.message.Address.parse
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage.text

@Service
@ConditionalOnProperty(
    prefix = "lukos.cli.control",
    name = ["send"],
    havingValue = "true",
    matchIfMissing = true
)
class SendCliCommand(
    val msh: MessageSenderHub
) : ICliCommand {

    override fun definition() = cliCommand("send") {
        description = "Send a message to a chat with platform and chat ID."

        argv {
            positional("target", ArgType.StringType) { required = true }
            positional("text", ArgType.StringType) { required = true; greedy = true }
            execute { args ->
                try {
                    val addr = parse(args.get("target"))
                    msh.send(text(addr, args.get("text")))
                    source.println("Successfully sent a message to $addr.")
                } catch (e: Exception) {
                    source.printlnErr("Failed to send: ${e.message}", e)
                }
            }
        }
    }

}
