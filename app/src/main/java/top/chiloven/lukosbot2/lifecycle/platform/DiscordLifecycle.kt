/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.lifecycle.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.core.BotCoroutineRuntime
import top.chiloven.lukosbot2.core.MessageDispatcher
import top.chiloven.lukosbot2.core.MessageSenderHub
import top.chiloven.lukosbot2.core.command.bot.CommandRegistry
import top.chiloven.lukosbot2.platform.ChatPlatform
import top.chiloven.lukosbot2.platform.discord.DiscordReceiver

@Component
@ConditionalOnProperty(
    prefix = "lukos.discord",
    name = ["enabled"],
    havingValue = "true"
)
class DiscordLifecycle(
    private val md: MessageDispatcher,
    private val msh: MessageSenderHub,
    private val props: AppProperties,
    private val proxyConfigProp: ProxyConfigProp,
    private val runtime: BotCoroutineRuntime,
    private val commandRegistry: CommandRegistry,
) : IPlatformAdapter {

    private val log = LogManager.getLogger(DiscordLifecycle::class.java)

    @Volatile
    private var running = false
    private var receiver: DiscordReceiver? = null
    private var collectorJob: Job? = null

    override fun start() {
        if (running) return
        try {
            val recv = DiscordReceiver(
                props.discord.token,
                proxyConfigProp,
                commandRegistry,
                runtime.dispatcher
            )

            // The collector can start before the connection: inbound updates are buffered in the
            // receiver channel until the JDA gateway is ready.
            collectorJob = runtime.launch("discord-inbound") {
                try {
                    recv.messages.collect {
                        md.receive(it)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("Discord inbound stream ended: {}", e.message, e)
                }
            }

            // DiscordSender needs the ready JDA instance, so the sender is registered only after start.
            runBlocking { recv.start() }
            msh.register(ChatPlatform.DISCORD, recv.sender())

            receiver = recv
            running = true
            log.info("[{}] started (prefix='{}')", name(), props.prefix)
            log.info("Discord ready")
        } catch (e: Exception) {
            collectorJob?.cancel()
            collectorJob = null
            receiver = null
            msh.unregister(ChatPlatform.DISCORD)
            throw RuntimeException("Failed to start " + name(), e)
        }
    }

    override fun stop() {
        try {
            receiver?.let { recv ->
                runBlocking { recv.stop() }
            }
        } catch (e: Exception) {
            log.warn("[{}] stop error: {}", name(), e.message, e)
        } finally {
            collectorJob?.cancel()
            collectorJob = null
            receiver = null
            running = false
        }
    }

    override fun name(): String = "Discord"

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = 0

    override fun stop(callback: Runnable) {
        try {
            stop()
        } finally {
            callback.run()
        }
    }

}
