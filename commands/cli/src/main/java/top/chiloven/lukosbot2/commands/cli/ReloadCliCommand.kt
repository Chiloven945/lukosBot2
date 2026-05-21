package top.chiloven.lukosbot2.commands.cli

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.ICliCommand
import top.chiloven.lukosbot2.core.IReloadControl
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.cliCommand

@Service
@ConditionalOnProperty(
    prefix = "lukos.cli.control",
    name = ["reload"],
    havingValue = "true",
    matchIfMissing = true
)
class ReloadCliCommand(
    private val reloadManager: IReloadControl
) : ICliCommand {

    override fun definition() = cliCommand("reload") {
        alias("rl")
        description = "Reload the whole bot or one/more modules."

        argv {
            positional("modules", ArgType.StringType) {
                required = false
                greedy = true
            }
            execute { args ->
                val raw = args.getOrNull<String>("modules")
                if (raw.isNullOrBlank()) {
                    Thread.ofVirtual().name("reload-bot").start {
                        try {
                            reloadManager.reloadWholeBot()
                            source.println("Reloaded whole bot.")
                        } catch (e: Exception) {
                            source.printlnErr("Reload failed: ${e.message}", e)
                        }
                    }
                } else {
                    val modules = raw.split(Regex("[,\\s]+"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    Thread.ofVirtual().name("reload-modules").start {
                        try {
                            val r = reloadManager.reloadModules(modules)
                            source.println("Reloaded: ${r.joinToString(", ")}")
                        } catch (e: Exception) {
                            source.printlnErr("Reload failed: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

}
