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
package top.chiloven.lukosbot2.util

import java.io.IOException
import java.nio.file.FileSystemException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import kotlin.math.min

internal data class RetryPolicy(
    val maxRetries: Int,
    val baseDelayMs: Long,
    val maxDelayMs: Long,
    val retryAfterCapMs: Long,
) {

    fun maxAttempts(): Int = 1 + max(0, maxRetries)

    fun shouldRetry(error: IOException): Boolean = when (error) {
        is HttpStatusException -> isRetryableStatus(error.statusCode)
        is FileSystemException -> false
        else -> true
    }

    fun delayMs(error: IOException, attemptIndex: Int): Long {
        val exp = 1L shl min(20, max(0, attemptIndex - 1))
        var delay = min(maxDelayMs, baseDelayMs * exp)

        if (error is HttpStatusException && error.retryAfterMs != null && error.retryAfterMs > 0) {
            delay = max(delay, min(retryAfterCapMs, error.retryAfterMs))
        }

        val jitter = ThreadLocalRandom.current().nextLong(0, max(1L, delay / 3))
        return min(retryAfterCapMs, delay + jitter)
    }

    private fun isRetryableStatus(code: Int): Boolean =
        code == 408 || code == 429 || code in 500..599

    companion object {

        fun default(maxRetries: Int): RetryPolicy = RetryPolicy(
            maxRetries = max(0, maxRetries),
            baseDelayMs = DownloadDefaults.DEFAULT_RETRY_BASE_DELAY_MS,
            maxDelayMs = DownloadDefaults.DEFAULT_RETRY_MAX_DELAY_MS,
            retryAfterCapMs = DownloadDefaults.DEFAULT_RETRY_AFTER_CAP_MS
        )

        fun parseRetryAfterMs(value: String?): Long? {
            if (value.isNullOrBlank()) return null
            value.trim().toLongOrNull()?.let { seconds ->
                if (seconds > 0) return seconds * 1000L
            }

            return try {
                val zdt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                max(0L, Duration.between(Instant.now(), zdt.toInstant()).toMillis())
            } catch (_: Exception) {
                null
            }
        }

        @Throws(IOException::class)
        fun sleepMs(ms: Long) {
            if (ms <= 0) return
            try {
                Thread.sleep(ms)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Sleep interrupted", ie)
            }
        }

    }

}
