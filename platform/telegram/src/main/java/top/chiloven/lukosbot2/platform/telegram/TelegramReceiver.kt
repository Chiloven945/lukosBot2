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
package top.chiloven.lukosbot2.platform.telegram

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage
import top.chiloven.lukosbot2.platform.ChatPlatform
import top.chiloven.lukosbot2.platform.IReceiver
import top.chiloven.lukosbot2.platform.ISender

/**
 * Telegram receiver: bridges the blocking Telegram SDK update consumer into a coroutine [Flow].
 */
class TelegramReceiver(
    token: String,
    username: String,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IReceiver {

    private val log = LogManager.getLogger(TelegramReceiver::class.java)

    private val stack = TelegramStack(token, username)
    private val channel = Channel<InboundMessage>(Channel.UNLIMITED)

    override val platform: ChatPlatform
        get() = ChatPlatform.TELEGRAM

    override val messages: Flow<InboundMessage> = channel.receiveAsFlow()

    override suspend fun start() {
        withContext(blockingDispatcher) {
            stack.ensureStarted()
            stack.bot.setSink { inbound ->
                val result = channel.trySend(inbound)
                if (result.isFailure) {
                    log.warn(
                        "Telegram receiver stopped; dropping inbound update {}",
                        inbound.addr()
                    )
                }
            }
        }
    }

    override suspend fun stop() {
        withContext(blockingDispatcher) {
            try {
                stack.close()
            } catch (e: Exception) {
                log.warn("Error while closing Telegram stack: {}", e.message, e)
            }
            channel.close()
        }
    }

    fun sender(): ISender = TelegramSender(stack, blockingDispatcher)

}
