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
package top.chiloven.lukosbot2.core

import kotlinx.coroutines.CancellationException
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.core.service.ServiceManager
import top.chiloven.lukosbot2.util.message.MessageIoLog
import java.util.concurrent.atomic.AtomicLong

/**
 * Entry point for inbound messages.
 *
 * <p>Processes messages concurrently so multiple commands can run at the same time (even in the same chat):
 * every [receive] launches an independent child coroutine in the [BotCoroutineRuntime] scope. Outbound
 * sending is still serialized per chat by [MessageSenderHub].</p>
 */
@Service
class MessageDispatcher(
    private val senderHub: MessageSenderHub,
    private val pipeline: PipelineProcessor,
    private val services: ServiceManager,
    private val runtime: BotCoroutineRuntime,
) {

    private val log = LogManager.getLogger(MessageDispatcher::class.java)
    private val sequence = AtomicLong()

    fun receive(inbound: InboundMessage?) {
        if (inbound?.addr() == null) return

        MessageIoLog.inbound(inbound)

        runtime.launch("message-${inbound.addr().chatId()}-${sequence.incrementAndGet()}") {
            try {
                val outs = ArrayList<OutboundMessage>()

                // 1) services (should see all messages)
                try {
                    val s = services.onMessage(inbound)
                    if (s.isNotEmpty()) outs.addAll(s)
                } catch (e: Exception) {
                    log.warn("Service processing error: {}", e.message, e)
                }

                // 2) command pipeline
                try {
                    val p = pipeline.handle(inbound)
                    if (p.isNotEmpty()) outs.addAll(p)
                } catch (e: Exception) {
                    log.warn("Pipeline processing error: {}", e.message, e)
                }

                // 3) send
                senderHub.sendBatch(outs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Unexpected dispatcher error: {}", e.message, e)
            }
        }
    }

}
