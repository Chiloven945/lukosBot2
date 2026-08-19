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

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.core.model.message.Address
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.platform.ChatPlatform
import top.chiloven.lukosbot2.platform.ISender
import top.chiloven.lukosbot2.util.message.MessageIoLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Central hub for routing [OutboundMessage] to the correct platform sender.
 *
 * <p>This hub also provides <b>per-chat ordering</b> guarantees: all outbound messages targeting
 * the same chat will be executed sequentially in submission order (striped by chat key).</p>
 *
 * <p>Implementation: 32 unbounded [Channel] lanes, each drained by one worker coroutine launched in the
 * [BotCoroutineRuntime] scope. The sender is snapshotted at enqueue time, so re-registering a platform
 * sender does not affect already queued messages. On shutdown all lanes are closed and drained before
 * returning (orderly shutdown), then the runtime scope is cancelled by Spring destroy ordering.</p>
 */
@Service
class MessageSenderHub(
    private val runtime: BotCoroutineRuntime,
) {

    private val log = LogManager.getLogger(MessageSenderHub::class.java)
    private val senders = ConcurrentHashMap<ChatPlatform, ISender>()

    private val laneCount = 32
    private val lanes: List<Channel<PendingSend>> = List(laneCount) { Channel(Channel.UNLIMITED) }

    private val workers = lanes.mapIndexed { index, lane ->
        runtime.launch("send-lane-" + index.toString().padStart(2, '0')) {
            for ((message, sender, key) in lane) {
                MessageIoLog.outbound(message)
                try {
                    sender.send(message)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("Failed to send outbound message to ${key}: ${e.message}", e)
                }
            }
        }
    }

    fun register(platform: ChatPlatform?, sender: ISender?) {
        if (platform == null || sender == null) return
        senders[platform] = sender
        log.info("Registered sender for platform {}", platform)
    }

    fun unregister(platform: ChatPlatform?) {
        if (platform == null) return
        senders.remove(platform)
        log.info("Unregistered sender for platform {}", platform)
    }

    fun sendBatch(outs: List<OutboundMessage>?) {
        if (outs.isNullOrEmpty()) return
        for (o in outs) {
            send(o)
        }
    }

    fun send(out: OutboundMessage?) {
        if (out == null) return
        val platform = out.addr().platform()

        val sender = senders[platform]
        if (sender == null) {
            log.warn("No sender registered for platform ${platform}, dropping outbound message.")
            return
        }

        val key = chatKey(out.addr())
        val lane = lanes[indexFor(key)]
        val result = lane.trySend(PendingSend(out, sender, key))
        if (result.isFailure) {
            log.warn("Send queue for $key is closed; dropping outbound message.")
        }
    }

    /**
     * Closes all lanes and drains the remaining queue before returning.
     */
    @PreDestroy
    fun destroy() {
        log.info("Shutting down sender hub: closing send lanes and draining queue")
        runBlocking {
            lanes.forEach { it.close() }
            workers.joinAll()
        }
    }

    private fun chatKey(addr: Address?): String {
        if (addr == null) return "unknown"
        return "${addr.platform().name}:${if (addr.group()) "g" else "p"}:${addr.chatId()}"
    }

    private fun indexFor(key: String): Int {
        var h = key.hashCode()
        h = h xor (h ushr 16)
        return h and (laneCount - 1)
    }

    private data class PendingSend(
        val message: OutboundMessage,
        val sender: ISender,
        val key: String,
    )

}
