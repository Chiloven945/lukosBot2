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

import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.*

internal class SingleFileDownloader(
    private val http: DownloadHttp,
    private val retryPolicyFactory: (Int) -> RetryPolicy,
) {

    private val log = LogManager.getLogger(SingleFileDownloader::class.java)

    @Throws(IOException::class)
    fun downloadToFile(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxRetries: Int,
    ) {
        val retryPolicy = retryPolicyFactory(maxRetries)
        val maxAttempts = retryPolicy.maxAttempts()

        targetFile.parent?.let(Files::createDirectories)
        val tmp = PathUtils.tempSiblingPath(targetFile)

        var success = false
        var ifRangeToken: String? = null
        val totalStartNs = System.nanoTime()

        try {
            for (attempt in 1..maxAttempts) {
                val attemptStartNs = System.nanoTime()
                var resumeFrom = 0L
                if (attempt > 1 && Files.exists(tmp)) {
                    resumeFrom = runCatching { Files.size(tmp) }.getOrDefault(0L)
                }

                try {
                    logStartAttempt(
                        attempt = attempt,
                        maxAttempts = maxAttempts,
                        url = url,
                        targetFile = targetFile,
                        resumeFrom = resumeFrom
                    )

                    var finalResumeFrom = resumeFrom
                    var usedRange = finalResumeFrom > 0

                    while (true) {
                        var restart = false
                        val request = if (usedRange) {
                            http.buildRangeGet(
                                url = url,
                                headers = headers,
                                rangeStart = finalResumeFrom,
                                ifRangeToken = ifRangeToken
                            )
                        } else {
                            http.buildGet(
                                url = url,
                                headers = headers
                            )
                        }

                        http.execute(request, timeoutMs).use { response ->
                            val code = response.code
                            http.pickIfRangeToken(response.headers)?.let { ifRangeToken = it }
                            http.debugResponseSummary(
                                url = url,
                                code = code,
                                headers = response.headers,
                                askedRange = usedRange,
                                rangeStart = finalResumeFrom
                            )

                            if (usedRange && code == 416) {
                                log.debug(
                                    "[DL] HTTP 416 for resume range, restart from scratch: url={}, tmp={}",
                                    url,
                                    tmp
                                )
                                PathUtils.deleteIfExistsQuietly(tmp)
                                usedRange = false
                                finalResumeFrom = 0L
                                restart = true
                                return@use
                            }

                            if (usedRange && code == 200) {
                                log.debug(
                                    "[DL] server ignored Range (got 200). Will restart writing from 0: url={}",
                                    url
                                )
                                usedRange = false
                                finalResumeFrom = 0L
                            }

                            if (code >= 400) {
                                throw HttpStatusException(
                                    statusCode = code,
                                    retryAfterMs = RetryPolicy.parseRetryAfterMs(response.header("Retry-After")),
                                    message = "HTTP $code"
                                )
                            }

                            writeResponseBody(
                                response = response,
                                code = code,
                                url = url,
                                targetFile = targetFile,
                                tmp = tmp,
                                attemptStartNs = attemptStartNs,
                                finalResumeFrom = finalResumeFrom
                            )
                        }

                        if (restart) continue

                        PathUtils.moveReplace(tmp, targetFile)
                        success = true

                        logSuccess(
                            url = url,
                            targetFile = targetFile,
                            totalStartNs = totalStartNs
                        )
                        return
                    }
                } catch (e: IOException) {
                    val willRetry = attempt < maxAttempts && retryPolicy.shouldRetry(e)
                    val delayMs = if (willRetry) retryPolicy.delayMs(e, attempt) else 0L

                    if (willRetry) {
                        log.warn(
                            "[DL] attempt {}/{} failed, will retry in {}ms: url={}, target={}, err={}",
                            attempt,
                            maxAttempts,
                            delayMs,
                            url,
                            targetFile,
                            e.toString()
                        )
                        RetryPolicy.sleepMs(delayMs)
                        continue
                    }

                    log.warn("[DL] failed (no more retries): url={}, target={}, err={}", url, targetFile, e.toString())
                    throw e
                }
            }

            throw IOException("Download failed unexpectedly (should not reach here)")
        } finally {
            if (!success) PathUtils.deleteIfExistsQuietly(tmp)
        }
    }

    private fun logStartAttempt(
        attempt: Int,
        maxAttempts: Int,
        url: URI,
        targetFile: Path,
        resumeFrom: Long,
    ) {
        if (!log.isDebugEnabled) return
        log.debug(
            "[DL] start attempt {}/{} url={} -> {}{}",
            attempt,
            maxAttempts,
            url,
            targetFile,
            if (resumeFrom > 0) " (resume@$resumeFrom)" else ""
        )
    }

    @Throws(IOException::class)
    private fun writeResponseBody(
        response: okhttp3.Response,
        code: Int,
        url: URI,
        targetFile: Path,
        tmp: Path,
        attemptStartNs: Long,
        finalResumeFrom: Long,
    ) {
        val totalSize = http.guessTotalSize(code, response.headers)
        val expectedBodyLen = response.body.contentLength()
        val options: Array<OpenOption> = if (finalResumeFrom > 0) {
            arrayOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        } else {
            arrayOf(
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
        }

        var writtenThisAttempt = 0L
        var lastLogNs = System.nanoTime()
        var lastLogBytes = finalResumeFrom
        val buffer = ByteArray(DownloadDefaults.BUFFER_SIZE)

        response.body.byteStream().use { input ->
            FileChannel.open(tmp, *options).use { channel ->
                var pos = finalResumeFrom
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    val bb = ByteBuffer.wrap(buffer, 0, read)
                    while (bb.hasRemaining()) {
                        val written = channel.write(bb, pos)
                        if (written <= 0) throw IOException("FileChannel write returned $written")
                        pos += written
                        writtenThisAttempt += written
                    }

                    if (log.isDebugEnabled) {
                        val now = System.nanoTime()
                        val intervalNs = Duration.ofMillis(
                            DownloadDefaults.DEFAULT_PROGRESS_LOG_INTERVAL_MS
                        ).toNanos()
                        if (now - lastLogNs >= intervalNs) {
                            val deltaBytes = pos - lastLogBytes
                            val deltaNs = now - lastLogNs
                            val pct = if (totalSize > 0) {
                                String.format(Locale.ROOT, "%.1f%%", pos * 100.0 / totalSize)
                            } else {
                                "?"
                            }

                            log.debug(
                                "[DL] progress {} -> {}: {} / {} ({}), instSpeed={}",
                                url,
                                targetFile,
                                DownloadFormatting.displayBytes(pos),
                                if (totalSize > 0) DownloadFormatting.displayBytes(totalSize) else "?",
                                pct,
                                DownloadFormatting.formatSpeed(deltaBytes, deltaNs)
                            )

                            lastLogBytes = pos
                            lastLogNs = now
                        }
                    }
                }

                if (expectedBodyLen > 0 && writtenThisAttempt != expectedBodyLen) {
                    throw IOException("Body length mismatch: expected=$expectedBodyLen, got=$writtenThisAttempt")
                }

                if (totalSize > 0 && pos != totalSize) {
                    throw IOException("Final size mismatch: expectedTotal=$totalSize, got=$pos")
                }

                val attemptMs = (System.nanoTime() - attemptStartNs) / 1_000_000
                log.debug(
                    "[DL] done url={} -> {}, bytes={}, cost={}ms, avgSpeed={}",
                    url,
                    targetFile,
                    DownloadFormatting.displayBytes(pos),
                    attemptMs,
                    DownloadFormatting.formatSpeed(pos, System.nanoTime() - attemptStartNs)
                )
            }
        }
    }

    private fun logSuccess(
        url: URI,
        targetFile: Path,
        totalStartNs: Long,
    ) {
        val totalMs = (System.nanoTime() - totalStartNs) / 1_000_000
        val finalSize = runCatching { Files.size(targetFile) }.getOrDefault(-1L)
        log.debug(
            "[DL] success url={} -> {}, finalSize={}, totalCost={}ms, totalAvgSpeed={}",
            url,
            targetFile,
            if (finalSize >= 0) DownloadFormatting.displayBytes(finalSize) else "?",
            totalMs,
            if (finalSize >= 0) {
                DownloadFormatting.formatSpeed(finalSize, System.nanoTime() - totalStartNs)
            } else {
                "?"
            }
        )
    }

}
