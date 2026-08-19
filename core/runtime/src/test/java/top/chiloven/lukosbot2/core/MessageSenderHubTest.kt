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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.core.model.message.Address
import top.chiloven.lukosbot2.core.model.message.outbound.OutText
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.platform.ChatPlatform
import top.chiloven.lukosbot2.platform.ISender
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

internal class RecordingSender(
    private val name: String,
    private val sendDelayMs: Long = 0,
    private val failOn: Set<Long> = emptySet(),
) : ISender {

    val texts = CopyOnWriteArrayList<String>()

    @Volatile
    private var active = 0

    @Volatile
    var maxActive = 0
        private set

    override suspend fun send(out: OutboundMessage) {
        active++
        maxActive = max(maxActive, active)
        try {
            if (failOn.contains(out.addr().chatId())) {
                throw IllegalStateException("sender $name failed for chat ${out.addr().chatId()}")
            }
            if (sendDelayMs > 0) delay(sendDelayMs.milliseconds)
            texts += (out.parts()!![0] as OutText).text()
        } finally {
            active--
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageSenderHubTest {

    private fun chatAddr(id: Long) = Address(
        ChatPlatform.TELEGRAM,
        id,
        false
    )

    private fun out(
        id: Long,
        text: String
    ) = OutboundMessage.text(chatAddr(id), text)

    private fun laneIndexOf(addr: Address): Int {
        val key = "${addr.platform().name}:${if (addr.group()) "g" else "p"}:${addr.chatId()}"
        var h = key.hashCode()
        h = h xor (h ushr 16)
        return h and 31
    }

    @Test
    fun `same chat messages are delivered in FIFO order`() = runTest {
        val runtime = BotCoroutineRuntime(
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
        )
        val hub = MessageSenderHub(runtime)
        val sender = RecordingSender("tg")
        hub.register(ChatPlatform.TELEGRAM, sender)

        hub.send(out(1, "m1"))
        hub.send(out(1, "m2"))
        hub.send(out(1, "m3"))

        testScheduler.advanceUntilIdle()
        assertEquals(listOf("m1", "m2", "m3"), sender.texts)

        runtime.destroy()
    }

    @Test
    fun `different chat lanes run concurrently`() = runTest {
        val runtime = BotCoroutineRuntime(
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
        )
        val hub = MessageSenderHub(runtime)
        val sender = RecordingSender("tg", sendDelayMs = 100)
        hub.register(ChatPlatform.TELEGRAM, sender)

        val idA = (1L..10_000L).first { laneIndexOf(chatAddr(it)) == 0 }
        val idB = (1L..10_000L).first { laneIndexOf(chatAddr(it)) == 1 }
        hub.send(out(idA, "a"))
        hub.send(out(idB, "b"))

        testScheduler.advanceUntilIdle()
        assertEquals(2, sender.maxActive)
        assertEquals(setOf("a", "b"), sender.texts.toSet())

        runtime.destroy()
    }

    @Test
    fun `sender failure is isolated and the lane keeps going`() = runTest {
        val runtime = BotCoroutineRuntime(
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
        )
        val hub = MessageSenderHub(runtime)
        val sender = RecordingSender("tg", failOn = setOf(1L))
        hub.register(ChatPlatform.TELEGRAM, sender)

        hub.send(out(1, "bad"))
        hub.send(out(2, "good"))

        testScheduler.advanceUntilIdle()
        assertEquals(listOf("good"), sender.texts)

        runtime.destroy()
    }

    @Test
    fun `sender is snapshotted at enqueue time`() = runTest {
        val runtime = BotCoroutineRuntime(
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
        )
        val hub = MessageSenderHub(runtime)
        val first = RecordingSender("first", sendDelayMs = 50)
        val second = RecordingSender("second")
        hub.register(ChatPlatform.TELEGRAM, first)

        hub.send(out(1, "m1"))
        hub.unregister(ChatPlatform.TELEGRAM)
        hub.register(ChatPlatform.TELEGRAM, second)
        hub.send(out(1, "m2"))

        testScheduler.advanceUntilIdle()
        assertEquals(listOf("m1"), first.texts)
        assertEquals(listOf("m2"), second.texts)

        runtime.destroy()
    }

    @Test
    fun `send without a registered sender drops the message silently`() = runTest {
        val runtime = BotCoroutineRuntime(
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
        )
        val hub = MessageSenderHub(runtime)

        hub.send(out(1, "m1"))
        testScheduler.advanceUntilIdle()

        runtime.destroy()
    }

    @Test
    fun `destroy drains the queue before returning`() {
        val runtime = BotCoroutineRuntime()
        val hub = MessageSenderHub(runtime)
        val sender = RecordingSender("tg", sendDelayMs = 5)
        hub.register(ChatPlatform.TELEGRAM, sender)

        repeat(20) { hub.send(out(1, "m$it")) }
        hub.destroy()

        assertEquals((0 until 20).map { "m$it" }, sender.texts)
    }

}
