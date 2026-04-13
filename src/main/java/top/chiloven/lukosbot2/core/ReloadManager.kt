package top.chiloven.lukosbot2.core

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.Main
import top.chiloven.lukosbot2.lifecycle.ConfigLifecycle
import top.chiloven.lukosbot2.lifecycle.platform.DiscordLifecycle
import top.chiloven.lukosbot2.lifecycle.platform.OneBotLifecycle
import top.chiloven.lukosbot2.lifecycle.platform.TelegramLifecycle
import top.chiloven.lukosbot2.platform.ChatPlatform
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class ReloadManager(
    private val configLifecycle: ConfigLifecycle,
    private val senderHub: MessageSenderHub,
    private val telegramProvider: ObjectProvider<TelegramLifecycle>,
    private val discordProvider: ObjectProvider<DiscordLifecycle>,
    private val onebotProvider: ObjectProvider<OneBotLifecycle>,
) {

    private val log: Logger = LogManager.getLogger(ReloadManager::class.java)
    private val reloadLock = ReentrantLock()

    fun supportedModules(): Set<String> =
        linkedSetOf("config", "telegram", "discord", "onebot", "bot", "all")

    fun reloadWholeBot() {
        reloadLock.withLock {
            Main.restart()
        }
    }

    fun reloadModules(names: Collection<String>): List<String> = reloadLock.withLock {
        val normalized = names
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .distinct()

        require(normalized.isNotEmpty()) { "No modules specified." }

        if (normalized.any { it == "bot" || it == "all" }) {
            Main.restart()
            return listOf("bot")
        }

        val done = mutableListOf<String>()

        for (name in normalized) {
            when (name) {
                "config", "conf", "cf" -> {
                    reloadConfig()
                    done += "config"
                }

                "telegram", "tg" -> {
                    reloadLifecycle(
                        name = "telegram",
                        platform = ChatPlatform.TELEGRAM,
                        lifecycle = telegramProvider.getIfAvailable()
                    )
                    done += "telegram"
                }

                "discord", "dc" -> {
                    reloadLifecycle(
                        name = "discord",
                        platform = ChatPlatform.DISCORD,
                        lifecycle = discordProvider.getIfAvailable()
                    )
                    done += "discord"
                }

                "onebot", "ob", "qq" -> {
                    reloadLifecycle(
                        name = "onebot",
                        platform = ChatPlatform.ONEBOT,
                        lifecycle = onebotProvider.getIfAvailable()
                    )
                    done += "onebot"
                }

                else -> {
                    throw IllegalArgumentException(
                        "Unknown module '$name'. Available: ${supportedModules().joinToString(", ")}"
                    )
                }
            }
        }

        done
    }

    private fun reloadConfig() {
        if (configLifecycle.isRunning) {
            configLifecycle.stop()
        }
        configLifecycle.start()
        log.info("Reloaded config module.")
    }

    private fun reloadLifecycle(
        name: String,
        platform: ChatPlatform,
        lifecycle: SmartLifecycle?
    ) {
        requireNotNull(lifecycle) {
            "Module '$name' is not enabled in current configuration."
        }

        senderHub.unregister(platform)

        if (lifecycle.isRunning) {
            lifecycle.stop()
            if (platform == ChatPlatform.TELEGRAM) {
                Thread.sleep(750)
            }
        }
        lifecycle.start()

        log.info("Reloaded module {}", name)
    }

}
