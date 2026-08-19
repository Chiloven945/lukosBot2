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
import top.chiloven.lukosbot2.core.BotCoroutineRuntime
import top.chiloven.lukosbot2.core.MessageDispatcher
import top.chiloven.lukosbot2.core.MessageSenderHub
import top.chiloven.lukosbot2.platform.ChatPlatform
import top.chiloven.lukosbot2.platform.telegram.TelegramReceiver

@Component
@ConditionalOnProperty(
    prefix = "lukos.telegram",
    name = ["enabled"],
    havingValue = "true"
)
class TelegramLifecycle(
    private val md: MessageDispatcher,
    private val msh: MessageSenderHub,
    private val props: AppProperties,
    private val runtime: BotCoroutineRuntime,
) : IPlatformAdapter {

    private val log = LogManager.getLogger(TelegramLifecycle::class.java)

    @Volatile
    private var running = false
    private var receiver: TelegramReceiver? = null
    private var collectorJob: Job? = null

    override fun start() {
        if (running) return
        try {
            val recv = TelegramReceiver(
                props.telegram.botToken,
                props.telegram.botUsername,
                runtime.dispatcher
            )

            // Register the sender first: TelegramSender only needs the pre-created HTTP client,
            // so outbound messages can be routed before the bot connection is up (no early-message
            // loss).
            msh.register(ChatPlatform.TELEGRAM, recv.sender())

            collectorJob = runtime.launch("telegram-inbound") {
                try {
                    recv.messages.collect { md.receive(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("Telegram inbound stream ended: {}", e.message, e)
                }
            }

            runBlocking { recv.start() }

            receiver = recv
            running = true
            log.info("[{}] started (prefix='{}')", name(), props.prefix)
            log.info("Telegram ready as @{}", props.telegram.botUsername)
        } catch (e: Exception) {
            collectorJob?.cancel()
            collectorJob = null
            receiver = null
            msh.unregister(ChatPlatform.TELEGRAM)
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

    override fun name(): String = "Telegram"

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
