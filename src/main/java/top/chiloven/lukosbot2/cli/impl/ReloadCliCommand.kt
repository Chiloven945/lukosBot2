package top.chiloven.lukosbot2.cli.impl

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.cli.ICliCommand
import top.chiloven.lukosbot2.core.ReloadManager
import top.chiloven.lukosbot2.core.cli.CliCmdContext
import top.chiloven.lukosbot2.util.brigadier.builder.CliLAB.literal
import top.chiloven.lukosbot2.util.brigadier.builder.CliRAB.argument

@Service
@ConditionalOnProperty(
    prefix = "lukos.cli.control",
    name = ["reload"],
    havingValue = "true",
    matchIfMissing = true
)
class ReloadCliCommand(
    private val reloadManager: ReloadManager
) : ICliCommand {

    override fun name(): String = "reload"

    override fun aliases(): List<String> = listOf("rl")

    override fun description(): String = "Reload the whole bot or one/more modules."

    override fun usage(): String = "reload [bot|all|config|telegram|discord|onebot][, ...]"

    override fun register(dispatcher: CommandDispatcher<CliCmdContext>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    val out = ctx.source

                    Thread.ofVirtual().name("reload-bot").start {
                        try {
                            out.println("§eReloading whole bot...§r")
                            reloadManager.reloadWholeBot()
                            out.println("§2Whole bot reloaded successfully.§r")
                        } catch (e: Exception) {
                            out.printlnErr("Failed to reload whole bot: ${e.message}", e)
                        }
                    }

                    1
                }
                .then(
                    argument("modules", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val out = ctx.source
                            val raw = StringArgumentType.getString(ctx, "modules")
                            val modules = raw
                                .split(Regex("[,\\s]+"))
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            Thread.ofVirtual().name("reload-modules").start {
                                try {
                                    val reloaded = reloadManager.reloadModules(modules)
                                    out.println("§2Reloaded: ${reloaded.joinToString(", ")}§r")
                                } catch (e: Exception) {
                                    out.printlnErr("Failed to reload module(s): ${e.message}", e)
                                }
                            }

                            1
                        }
                )
        )
    }

}
