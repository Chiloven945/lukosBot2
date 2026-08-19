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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import java.lang.Runnable
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/**
 * Application-owned coroutine runtime: the single structured scope for all bot background work.
 *
 * <p>Owns the root [SupervisorJob] that all message dispatch, send lanes, receivers, scheduled services and CLI
 * console work run under. Spring beans inject this runtime instead of creating their own executors or virtual
 * threads, so everything is cancelled together when the application context shuts down.</p>
 *
 * <p>The dispatcher defaults to [Dispatchers.IO] because command and service work is blocking-heavy
 * (HTTP, JDBC, SDK calls). Tests inject a test dispatcher instead.</p>
 *
 * <p>Task exception policy: non-cancellation exceptions thrown by [Runnable] tasks submitted through the
 * Java-friendly bridges ([launchBlockingTask], [schedule], [scheduleAtFixedRate]) are caught and logged as
 * warnings; the job survives and, for fixed-rate schedules, the next tick still runs. [CancellationException]
 * is always rethrown. Callers of [launch] own their own exception handling.</p>
 *
 * <p>On shutdown ([destroy]) the root job is cancelled but never joined: joining would deadlock when the
 * shutdown is triggered from a child coroutine (for example the CLI console running {@code appControl.shutdown()}
 * or {@code appControl.restart()}). Dependents that need draining (for example [MessageSenderHub]) join their
 * own children in their own destroy methods before this bean is destroyed (Spring destroys constructor-injected
 * dependencies last).</p>
 */
@Service
class BotCoroutineRuntime(
    /**
     * Blocking-capable dispatcher for suspend boundaries that wrap blocking SDK calls.
     * Defaults to [Dispatchers.IO]; tests inject a test dispatcher.
     */
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val monotonicTimeMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    private val log = LogManager.getLogger(BotCoroutineRuntime::class.java)

    private val rootJob = SupervisorJob()
    private val scope = CoroutineScope(rootJob + dispatcher + CoroutineName("lukos-runtime"))

    /**
     * Launches a child coroutine in the runtime scope. The returned [Job] can be cancelled by the caller.
     * Callers own the exception policy for the block.
     */
    fun launch(
        name: String,
        block: suspend CoroutineScope.() -> Unit
    ): Job = scope.launch(
        CoroutineName(name),
        block = block
    )

    /**
     * Launches a blocking task on the runtime dispatcher. See the class docs for the exception policy.
     */
    fun launchBlockingTask(name: String, task: Runnable): ICancellableTask =
        taskHandle(scope.launch(CoroutineName(name)) {
            try {
                task.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Task '{}' failed: {}", name, e.message, e)
            }
        })

    /**
     * Runs [task] once after [delayMs]. See the class docs for the exception policy.
     */
    fun schedule(
        name: String,
        delayMs: Long,
        task: Runnable
    ): ICancellableTask =
        taskHandle(scope.launch(CoroutineName(name)) {
            delay(delayMs.milliseconds)
            try {
                task.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Scheduled task '{}' failed: {}", name, e.message, e)
            }
        })

    /**
     * Runs [task] at a fixed rate: each tick starts [periodMs] after the previous tick started. If a tick
     * overruns the period the next tick starts immediately after it finishes (fixed-rate, no overlap), matching
     * {@code ScheduledExecutorService.scheduleAtFixedRate}. See the class docs for the exception policy.
     */
    fun scheduleAtFixedRate(
        name: String,
        initialDelayMs: Long,
        periodMs: Long,
        task: Runnable,
    ): ICancellableTask =
        taskHandle(scope.launch(CoroutineName(name)) {
            delay(initialDelayMs.milliseconds)
            while (isActive) {
                val startedAtMs = monotonicTimeMs()
                try {
                    task.run()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("Scheduled task '{}' failed: {}", name, e.message, e)
                }
                val elapsedMs = monotonicTimeMs() - startedAtMs
                delay(max(0L, periodMs - elapsedMs).milliseconds)
            }
        })

    /**
     * Cancels the whole runtime scope without joining. See the class docs for why this must not join.
     */
    @PreDestroy
    fun destroy() {
        log.info("Cancelling bot coroutine runtime scope")
        rootJob.cancel()
    }

    private fun taskHandle(job: Job): ICancellableTask = object : ICancellableTask {

        override fun cancel() {
            job.cancel()
        }

    }

}
