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

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.core.model.message.Address
import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage
import top.chiloven.lukosbot2.core.model.message.outbound.OutText
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.platform.ChatPlatform
import kotlin.time.Duration.Companion.milliseconds

class PipelineProcessorTest {

    private val addr = Address(ChatPlatform.TELEGRAM, 1L, false)
    private val inbound = InboundMessage(
        addr,
        null,
        null,
        null,
        null,
        null
    )

    private class FakeProcessor(
        private val tag: String,
        private val delayMs: Long = 0,
        private val fail: Boolean = false,
    ) : IProcessor {

        override suspend fun handle(inbound: InboundMessage): List<OutboundMessage> {
            if (delayMs > 0) delay(delayMs.milliseconds)
            check(!fail) { "processor $tag failed" }
            return listOf(OutboundMessage.text(inbound.addr(), "[$tag]"))
        }

    }

    private fun texts(outs: List<OutboundMessage>): List<String> =
        outs.map { (it.parts()!![0] as OutText).text() }

    @Test
    fun `empty pipeline returns empty list`() = runTest {
        val pipeline = PipelineProcessor(null)
        assertEquals(emptyList<OutboundMessage>(), pipeline.handle(inbound))
    }

    @Test
    fun `processors run in order and outputs are concatenated`() = runTest {
        val pipeline = PipelineProcessor(listOf(
            FakeProcessor("a"),
            FakeProcessor("b"),
            FakeProcessor("c")
        ))
        assertEquals(
            listOf("[a]", "[b]", "[c]"),
            texts(pipeline.handle(inbound))
        )
    }

    @Test
    fun `suspend processors are awaited sequentially`() = runTest {
        val order = mutableListOf<String>()
        val slow = object : IProcessor {
            override suspend fun handle(inbound: InboundMessage): List<OutboundMessage> {
                delay(100.milliseconds)
                order += "a"
                return emptyList()
            }
        }
        val fast = object : IProcessor {
            override suspend fun handle(inbound: InboundMessage): List<OutboundMessage> {
                order += "b"
                return emptyList()
            }
        }
        val pipeline = PipelineProcessor(listOf(slow, fast))

        pipeline.handle(inbound)
        assertEquals(listOf("a", "b"), order)
    }

    @Test
    fun `processor exception propagates to the caller`() = runTest {
        val pipeline = PipelineProcessor(listOf(
                FakeProcessor("a"),
                FakeProcessor("b", fail = true)
            )
        )
        try {
            pipeline.handle(inbound)
            org.junit.jupiter.api.Assertions.fail("expected IllegalStateException")
        } catch (ex: IllegalStateException) {
            assertTrue(ex.message!!.contains("b"))
        }
    }

}
