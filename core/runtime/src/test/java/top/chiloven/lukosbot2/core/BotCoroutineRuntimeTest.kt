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
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class BotCoroutineRuntimeTest {

    private fun TestScope.testRuntime(): BotCoroutineRuntime {
        return BotCoroutineRuntime(
            dispatcher = StandardTestDispatcher(testScheduler),
            monotonicTimeMs = { testScheduler.currentTime },
        )
    }

    @Test
    fun `one-shot schedule fires after the delay and only once`() = runTest {
        val runtime = testRuntime()
        var count = 0
        runtime.schedule("one-shot", 1_000) { count++ }

        advanceTimeBy(999.milliseconds)
        runCurrent()
        assertEquals(0, count)

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(1, count)

        advanceTimeBy(10_000.milliseconds)
        runCurrent()
        assertEquals(1, count)

        runtime.destroy()
    }

    @Test
    fun `cancelled one-shot schedule never fires`() = runTest {
        val runtime = testRuntime()
        var count = 0
        val task = runtime.schedule("cancelled", 1_000) { count++ }

        task.cancel()
        advanceTimeBy(10_000.milliseconds)
        runCurrent()
        assertEquals(0, count)

        runtime.destroy()
    }

    @Test
    fun `fixed rate schedule ticks at constant cadence`() = runTest {
        val runtime = testRuntime()
        val ticks = mutableListOf<Long>()
        runtime.scheduleAtFixedRate("fixed", 0, 1_000) { ticks += testScheduler.currentTime }

        runCurrent()
        advanceTimeBy(1_000.milliseconds)
        runCurrent()
        advanceTimeBy(1_000.milliseconds)
        runCurrent()
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), ticks)

        runtime.destroy()
    }

    @Test
    fun `overrunning task resumes immediately without overlap`() = runTest {
        var fakeClock = 0L
        val runtime = BotCoroutineRuntime(
            dispatcher = StandardTestDispatcher(testScheduler),
            monotonicTimeMs = { fakeClock },
        )

        val ticks = mutableListOf<Long>()
        lateinit var handle: ICancellableTask
        handle = runtime.scheduleAtFixedRate("slow", 0, 1_000) {
            ticks += fakeClock
            fakeClock += 1_500 // simulate a long-running task
            if (ticks.size == 3) {
                handle.cancel()
            }
        }

        runCurrent()

        assertEquals(listOf(0L, 1_500L, 3_000L), ticks)

        runtime.destroy()
    }

    @Test
    fun `throwing task does not kill the schedule`() = runTest {
        val runtime = testRuntime()
        var count = 0
        runtime.scheduleAtFixedRate("flaky", 0, 1_000) {
            count++
            if (count == 1) throw IllegalStateException("boom")
        }

        runCurrent()
        advanceTimeBy(1_000.milliseconds)
        runCurrent()
        advanceTimeBy(1_000.milliseconds)
        runCurrent()

        assertEquals(3, count)

        runtime.destroy()
    }

    @Test
    fun `destroy cancels all children`() = runTest {
        val runtime = testRuntime()
        var ran = false
        runtime.launch("x") { delay(10_000.milliseconds); ran = true }

        advanceTimeBy(5_000.milliseconds)
        runtime.destroy()
        advanceUntilIdle()
        assertFalse(ran)

        runtime.launch("y") { ran = true }
        advanceUntilIdle()
        assertFalse(ran)
    }

    @Test
    fun `launchBlockingTask runs the runnable in the scope`() = runTest {
        val runtime = testRuntime()
        var ran = false
        val task = runtime.launchBlockingTask("bridge") { ran = true }

        assertNotNull(task)
        runCurrent()
        assertTrue(ran)

        runtime.destroy()
    }

}
