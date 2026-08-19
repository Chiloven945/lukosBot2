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
package top.chiloven.lukosbot2.lifecycle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.core.BotCoroutineRuntime
import top.chiloven.lukosbot2.core.command.cli.CliCmdContext
import top.chiloven.lukosbot2.core.command.cli.CliCmdProcessor
import java.io.*

@Service
@ConditionalOnProperty(
    prefix = "lukos.cli", name = ["enabled"], havingValue = "true"
)
class ConsoleLifecycle(
    private val processor: CliCmdProcessor,
    private val runtime: BotCoroutineRuntime,
) : SmartLifecycle {

    private val log = LogManager.getLogger(ConsoleLifecycle::class.java)
    private val context = CliCmdContext(System.out)

    @Volatile
    private var running = false
    private var job: Job? = null

    override fun start() {
        if (running) return
        running = true

        job = runtime.launch("console") {
            try {
                val reader = BufferedReader(InputStreamReader(NonClosingInputStream(System.`in`)))
                while (running && isActive) {
                    val line =
                        runInterruptible { reader.readLine() }
                            ?: break
                    if (line.isBlank()) continue

                    try {
                        processor.handle(line, context)
                    } catch (e: Exception) {
                        log.warn(
                            "[Cli] Failed to execute command: {}", e.message, e
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (running && !isExpectedShutdown(e)) {
                    log.warn(
                        "[Cli] Console lifecycle stopped unexpectedly: {}",
                        e.message,
                        e
                    )
                }
            } finally {
                running = false
            }
        }
    }

    override fun stop() {
        running = false
        job?.cancel()
        job = null
    }

    override fun isRunning(): Boolean = running

    override fun stop(callback: Runnable) {
        stop()
        callback.run()
    }

    override fun getPhase(): Int = 0

    private companion object {

        fun isExpectedShutdown(e: Exception): Boolean {
            return e is IOException && "Stream closed".equals(
                e.message, ignoreCase = true
            ) || e is InterruptedException
        }
    }

    /**
     * Never closes System.in, otherwise a whole-process restart cannot recreate the console lifecycle.
     */
    private class NonClosingInputStream(inner: InputStream) : FilterInputStream(inner) {

        override fun close() {
            // no-op
        }

    }

}
