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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.config.ServiceConfigProp
import top.chiloven.lukosbot2.core.model.message.Address
import top.chiloven.lukosbot2.core.model.message.inbound.InboundMessage
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.core.service.ServiceManager
import top.chiloven.lukosbot2.core.service.ServiceRegistry
import top.chiloven.lukosbot2.core.state.Scope
import top.chiloven.lukosbot2.core.state.ScopeType
import top.chiloven.lukosbot2.core.state.store.IStateStore
import top.chiloven.lukosbot2.platform.ChatPlatform
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private class FakeStateStore : IStateStore {

    private val store = ConcurrentHashMap<String, MutableMap<String, String>>()

    private fun key(scope: Scope, namespace: String) = "$scope|$namespace"

    override fun getJson(
        scope: Scope,
        namespace: String,
        key: String
    ): Optional<String> = Optional.ofNullable(
        store[key(scope, namespace)]?.get(key)
    )

    override fun getNamespaceJson(
        scope: Scope,
        namespace: String
    ): Map<String, String> =
        store[key(scope, namespace)]?.toMap()
            ?: emptyMap()

    override fun upsertJson(
        scope: Scope,
        namespace: String,
        key: String,
        json: String,
        expiresAtOrNull: Instant?,
    ) {
        store.computeIfAbsent(key(scope, namespace)) { ConcurrentHashMap() }[key] = json
    }

    override fun delete(
        scope: Scope,
        namespace: String,
        key: String
    ) {
        store[key(scope, namespace)]?.remove(key)
    }

    override fun scanByScopeTypeAndNamespace(
        type: ScopeType,
        namespace: String
    ): Map<String, Map<String, String>> {
        return store
                .filterKeys { it.startsWith("$type|") }
                .mapValues { it.value.toMap() }
                .map { (k, v) -> k.substringAfter('|') to v }
                .filter { (_, v) -> v.isNotEmpty() }
                .toMap()
    }
}

private class ThrowingServiceManager(
    registry: ServiceRegistry,
    store: IStateStore,
    senderHub: MessageSenderHub,
    props: ServiceConfigProp,
    runtime: BotCoroutineRuntime,
) : ServiceManager(registry, store, senderHub, props, runtime) {

    override fun onMessage(`in`: InboundMessage?): List<OutboundMessage> {
        throw IllegalStateException("service layer failure")
    }

}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageDispatcherTest {

    private val addr1 = Address(ChatPlatform.TELEGRAM, 1L, false)
    private val addr2 = Address(ChatPlatform.TELEGRAM, 2L, false)

    private fun inbound(addr: Address) = InboundMessage(
        addr,
        null,
        null,
        null,
        null,
        null
    )

    private fun newDispatcher(
        runtime: BotCoroutineRuntime,
        processors: List<IProcessor>,
    ): Triple<MessageSenderHub, RecordingSender, MessageDispatcher> {
        val hub = MessageSenderHub(runtime)
        val sender = RecordingSender("tg")

        hub.register(ChatPlatform.TELEGRAM, sender)

        val services = ServiceManager(
            ServiceRegistry(emptyList()),
            FakeStateStore(),
            hub,
            ServiceConfigProp(),
            runtime
        )
        val dispatcher = MessageDispatcher(
            hub,
            PipelineProcessor(processors),
            services,
            runtime
        )

        return Triple(hub, sender, dispatcher)
    }

    @Test
    fun `messages in the same chat are processed concurrently`() = runTest {
        val runtime = BotCoroutineRuntime(
            StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
        )
        val gate = CompletableDeferred<Unit>()
        var entered = 0

        val proc = object : IProcessor {
            override suspend fun handle(inbound: InboundMessage): List<OutboundMessage> {
                entered++
                gate.await()
                return listOf(OutboundMessage.text(inbound.addr(), "reply"))
            }
        }

        val (_, sender, dispatcher) = newDispatcher(runtime, listOf(proc))

        dispatcher.receive(inbound(addr1))
        runCurrent()
        assertEquals(1, entered)

        dispatcher.receive(inbound(addr1))
        runCurrent()
        assertEquals(2, entered)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("reply", "reply"), sender.texts)

        runtime.destroy()
    }

    @Test
    fun `service failure does not block the pipeline`() = runTest {
        val runtime = BotCoroutineRuntime(
            StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
        )
        val hub = MessageSenderHub(runtime)
        val sender = RecordingSender("tg")

        hub.register(ChatPlatform.TELEGRAM, sender)

        val proc = object : IProcessor {
            override suspend fun handle(inbound: InboundMessage): List<OutboundMessage> =
                listOf(OutboundMessage.text(inbound.addr(), "from-pipeline"))
        }

        val services = ThrowingServiceManager(
            ServiceRegistry(emptyList()),
            FakeStateStore(),
            hub,
            ServiceConfigProp(),
            runtime,
        )
        val dispatcher = MessageDispatcher(
            hub,
            PipelineProcessor(listOf(proc)),
            services,
            runtime
        )

        dispatcher.receive(inbound(addr1))
        advanceUntilIdle()
        assertEquals(listOf("from-pipeline"), sender.texts)

        runtime.destroy()
    }

    @Test
    fun `pipeline failure does not cancel the runtime scope`() = runTest {
        val runtime = BotCoroutineRuntime(
            StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
        )
        var calls = 0
        val proc = object : IProcessor {
            override suspend fun handle(inbound: InboundMessage): List<OutboundMessage> {
                calls++
                if (calls == 1) throw IllegalStateException("pipeline boom")
                return listOf(OutboundMessage.text(inbound.addr(), "ok"))
            }
        }
        val (_, sender, dispatcher) = newDispatcher(runtime, listOf(proc))

        dispatcher.receive(inbound(addr1))
        advanceUntilIdle()
        assertEquals(emptyList<String>(), sender.texts)

        dispatcher.receive(inbound(addr2))
        advanceUntilIdle()
        assertEquals(listOf("ok"), sender.texts)

        runtime.destroy()
    }

    @Test
    fun `cancellation of the runtime cancels in-flight processing`() = runTest {
        val runtime = BotCoroutineRuntime(
            StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
        )
        val gate = CompletableDeferred<Unit>()
        val proc = object : IProcessor {
            override suspend fun handle(inbound: InboundMessage): List<OutboundMessage> {
                gate.await()
                return listOf(OutboundMessage.text(inbound.addr(), "late"))
            }
        }
        val (_, sender, dispatcher) = newDispatcher(runtime, listOf(proc))

        dispatcher.receive(inbound(addr1))
        runCurrent()
        runtime.destroy()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), sender.texts)
    }

}
