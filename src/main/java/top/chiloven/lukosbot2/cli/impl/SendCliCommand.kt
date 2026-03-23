package top.chiloven.lukosbot2.cli.impl

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.cli.ICliCommand
import top.chiloven.lukosbot2.core.MessageSenderHub
import top.chiloven.lukosbot2.core.cli.CliCmdContext
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage.text
import top.chiloven.lukosbot2.util.brigadier.arguments.AddressArgumentType
import top.chiloven.lukosbot2.util.brigadier.builder.CliLAB.literal
import top.chiloven.lukosbot2.util.brigadier.builder.CliRAB.argument

@Service
class SendCliCommand(
    val msh: MessageSenderHub
) : ICliCommand {

    override fun name(): String = "send"

    override fun description(): String = "Send a message to a chat with platform and chat ID."

    override fun usage(): String = "send <platform>:<p|g>:<id> <text>"

    override fun register(dispatcher: CommandDispatcher<CliCmdContext>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    ctx.source.printlnErr("Usage: ${usage()}")
                    0
                }
                .then(
                    argument("target", AddressArgumentType.address())
                        .then(
                            argument("text", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    val addr = AddressArgumentType.getAddress(ctx, "target")
                                    val msg = StringArgumentType.getString(ctx, "text")

                                    msh.send(text(addr, msg))
                                    ctx.source.println(
                                        "§2Successfully sent a message to ${addr}.§r"
                                    )
                                    1
                                }
                        )
                )
        )
    }

}
