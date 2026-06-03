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
package top.chiloven.lukosbot2.util.download

import okhttp3.Response
import org.apache.logging.log4j.LogManager
import top.chiloven.lukosbot2.util.HttpStatusException
import top.chiloven.lukosbot2.util.PathUtils
import top.chiloven.lukosbot2.util.concurrent.Coroutines
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.max
import kotlin.math.min

internal class RangeDownloader(
    private val http: DownloadHttp,
    private val singleFileDownloader: SingleFileDownloader,
    private val retryPolicyFactory: (Int) -> RetryPolicy,
) {

    private val log = LogManager.getLogger(RangeDownloader::class.java)

    @Throws(IOException::class)
    fun downloadToFileFast(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        chunkThreads: Int,
        minSizeForChunking: Long,
        minPartSizeBytes: Long,
        maxRetries: Int,
    ) {
        val threads = max(1, chunkThreads)
        if (threads == 1) {
            log.debug("[DL-FAST] chunkThreads<=1, fallback to single: url={}", url)
            singleFileDownloader.downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
            return
        }

        val probeStartNs = System.nanoTime()
        val meta = try {
            probeRangeMeta(url, headers, timeoutMs)
        } catch (ex: Exception) {
            log.debug("[DL-FAST] Range probe failed, fallback to single: url={}, err={}", url, ex.toString())
            singleFileDownloader.downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
            return
        } finally {
            val probeMs = (System.nanoTime() - probeStartNs) / 1_000_000
            log.debug("[DL-FAST] probe cost={}ms, url={}", probeMs, url)
        }

        log.debug(
            "[DL-FAST] rangeMeta url={}, acceptRanges={}, length={}, ifRangeToken={}",
            url,
            meta.acceptRanges,
            meta.length,
            if (meta.ifRangeToken != null) "yes" else "no"
        )

        if (!meta.acceptRanges || meta.length <= 0 || meta.length < max(1L, minSizeForChunking)) {
            log.debug(
                "[DL-FAST] not chunking (acceptRanges={}, len={}, min={}): url={}",
                meta.acceptRanges,
                meta.length,
                minSizeForChunking,
                url
            )
            singleFileDownloader.downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
            return
        }

        val total = meta.length
        val partPlan = planParts(
            total = total,
            chunkThreads = threads,
            minPartSizeBytes = minPartSizeBytes
        )
        if (partPlan.size < 2) {
            singleFileDownloader.downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
            return
        }

        downloadPartsOrFallback(
            url = url,
            targetFile = targetFile,
            headers = headers,
            timeoutMs = timeoutMs,
            maxRetries = maxRetries,
            total = total,
            parts = partPlan,
            ifRangeToken = meta.ifRangeToken
        )
    }

    @Throws(IOException::class)
    fun probeRangeMeta(
        url: URI,
        headers: Map<String, String>?,
        timeoutMs: Int,
    ): RangeMeta {
        try {
            http.execute(http.buildHead(url, headers), timeoutMs).use { response ->
                val code = response.code
                http.debugResponseSummary(url, code, response.headers, false, 0)
                if (code < 400) {
                    val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    val accept = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    val token = http.pickIfRangeToken(response.headers)
                    if (accept && len > 0) {
                        log.debug(
                            "[DL-PROBE] HEAD says acceptRanges=true len={} url={}",
                            DownloadFormatting.displayBytes(len),
                            url
                        )
                        return RangeMeta(len, true, token)
                    }

                    log.debug(
                        "[DL-PROBE] HEAD insufficient (acceptRanges={}, len={}) url={}",
                        accept,
                        if (len > 0) DownloadFormatting.displayBytes(len) else "unknown",
                        url
                    )
                }
            }
        } catch (_: Exception) {
            log.debug("[DL-PROBE] HEAD failed, will try Range probe: url={}", url)
        }

        http.execute(
            http.buildRangeGet(
                url = url,
                headers = headers,
                rangeStart = 0L,
                rangeEnd = 0L
            ),
            timeoutMs
        ).use { response ->
            val code = response.code
            http.debugResponseSummary(url, code, response.headers, true, 0)
            if (code == 206) {
                val total = response.header("Content-Range")?.let(http::parseTotalFromContentRange) ?: -1L
                val token = http.pickIfRangeToken(response.headers)
                if (total > 0) {
                    log.debug(
                        "[DL-PROBE] Range probe OK: acceptRanges=true total={} url={}",
                        DownloadFormatting.displayBytes(total),
                        url
                    )
                    return RangeMeta(total, true, token)
                }

                log.debug("[DL-PROBE] Range probe OK but total unknown: url={}", url)
                return RangeMeta(-1L, true, token)
            }

            log.debug("[DL-PROBE] Range probe not supported (code={}): url={}", code, url)
            return RangeMeta(-1L, false, null)
        }
    }

    fun planParts(
        total: Long,
        chunkThreads: Int,
        minPartSizeBytes: Long,
    ): List<RangePart> {
        val minPart = max(256 * 1024L, minPartSizeBytes)
        val maxPartsBySize = ((total + minPart - 1) / minPart).coerceAtLeast(1L)
        val parts = max(2, min(max(1, chunkThreads), min(Int.MAX_VALUE.toLong(), maxPartsBySize).toInt()))
        val partSize = (total + parts - 1) / parts
        val result = mutableListOf<RangePart>()

        for (i in 0 until parts) {
            val start = i * partSize
            val end = min(total - 1, start + partSize - 1)
            if (start > end) break
            result += RangePart(
                index = i + 1,
                start = start,
                end = end
            )
        }

        return result
    }

    @Throws(IOException::class)
    private fun downloadPartsOrFallback(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxRetries: Int,
        total: Long,
        parts: List<RangePart>,
        ifRangeToken: String?,
    ) {
        targetFile.parent?.let(Files::createDirectories)
        val tmp = PathUtils.tempSiblingPath(targetFile)
        var chunkOk = false
        val fileStartNs = System.nanoTime()

        try {
            PathUtils.deleteIfExistsQuietly(tmp)
            RandomAccessFile(tmp.toFile(), "rw").use { it.setLength(total) }

            val partSize = parts.firstOrNull()?.let { it.end - it.start + 1 } ?: 0L
            log.debug(
                "[DL-FAST] plan url={}, total={}, parts={}, partSize~={}, tmp={}",
                url,
                DownloadFormatting.displayBytes(total),
                parts.size,
                DownloadFormatting.displayBytes(partSize),
                tmp
            )

            Coroutines.runBlockingIo {
                Coroutines.mapLimited(parts, parts.size) { part ->
                    downloadRangeToFile(
                        url = url,
                        tmpFile = tmp,
                        part = part,
                        headers = headers,
                        timeoutMs = timeoutMs,
                        maxRetries = maxRetries,
                        ifRangeToken = ifRangeToken
                    )
                }
            }

            PathUtils.moveReplace(tmp, targetFile)
            chunkOk = true

            val costMs = (System.nanoTime() - fileStartNs) / 1_000_000
            log.debug(
                "[DL-FAST] success url={} -> {}, bytes={}, cost={}ms, avgSpeed={}",
                url,
                targetFile,
                DownloadFormatting.displayBytes(total),
                costMs,
                DownloadFormatting.formatSpeed(total, System.nanoTime() - fileStartNs)
            )
        } catch (ex: FileSystemException) {
            PathUtils.deleteIfExistsQuietly(tmp)
            throw ex
        } catch (ex: IOException) {
            val costMs = (System.nanoTime() - fileStartNs) / 1_000_000
            log.warn(
                "[DL-FAST] chunked download failed after {}ms, will fallback to single: url={}, err={}",
                costMs,
                url,
                ex.toString()
            )
        } finally {
            if (!chunkOk) PathUtils.deleteIfExistsQuietly(tmp)
        }

        if (!chunkOk) {
            singleFileDownloader.downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
        }
    }

    @Throws(IOException::class)
    private fun downloadRangeToFile(
        url: URI,
        tmpFile: Path,
        part: RangePart,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxRetries: Int,
        ifRangeToken: String?,
    ) {
        val expected = part.end - part.start + 1
        if (expected <= 0) return

        val retryPolicy = retryPolicyFactory(maxRetries)
        val maxAttempts = retryPolicy.maxAttempts()

        for (attempt in 1..maxAttempts) {
            val startedNs = System.nanoTime()
            try {
                logPartStart(part, expected, attempt, maxAttempts, url)

                http.execute(
                    http.buildRangeGet(
                        url = url,
                        headers = headers,
                        rangeStart = part.start,
                        rangeEnd = part.end,
                        ifRangeToken = ifRangeToken
                    ),
                    timeoutMs
                ).use { response ->
                    val code = response.code
                    http.debugResponseSummary(url, code, response.headers, true, part.start)

                    if (code >= 400) {
                        throw HttpStatusException.fromResponse(response)
                    }
                    if (code != 206) {
                        throw IOException("Expected HTTP 206, got HTTP $code")
                    }

                    val written = writePartBody(
                        response = response,
                        tmpFile = tmpFile,
                        start = part.start
                    )

                    if (written != expected) {
                        throw IOException(
                            "Range bytes mismatch: expected=$expected, got=$written, range=${part.start}-${part.end}"
                        )
                    }
                }

                val elapsedNs = System.nanoTime() - startedNs
                log.debug(
                    "[DL-PART] done part#{} range {}-{}, bytes={}, cost={}ms, avgSpeed={}",
                    part.index,
                    part.start,
                    part.end,
                    DownloadFormatting.displayBytes(expected),
                    elapsedNs / 1_000_000,
                    DownloadFormatting.formatSpeed(expected, elapsedNs)
                )
                return
            } catch (e: IOException) {
                val willRetry = attempt < maxAttempts && retryPolicy.shouldRetry(e)
                val delayMs = if (willRetry) retryPolicy.delayMs(e, attempt) else 0L
                if (willRetry) {
                    log.warn(
                        "[DL-PART] part#{} attempt {}/{} failed, retry in {}ms: url={}, err={}",
                        part.index,
                        attempt,
                        maxAttempts,
                        delayMs,
                        url,
                        e.toString()
                    )
                    RetryPolicy.sleepMs(delayMs)
                    continue
                }

                log.warn("[DL-PART] part#{} failed (no more retries): url={}, err={}", part.index, url, e.toString())
                throw e
            }
        }

        throw IOException("Part download failed unexpectedly (should not reach here)")
    }

    private fun logPartStart(
        part: RangePart,
        expected: Long,
        attempt: Int,
        maxAttempts: Int,
        url: URI,
    ) {
        if (!log.isDebugEnabled) return
        log.debug(
            "[DL-PART] start part#{} range {}-{} ({}), attempt {}/{} url={}",
            part.index,
            part.start,
            part.end,
            DownloadFormatting.displayBytes(expected),
            attempt,
            maxAttempts,
            url
        )
    }

    @Throws(IOException::class)
    private fun writePartBody(
        response: Response,
        tmpFile: Path,
        start: Long,
    ): Long {
        var written = 0L
        val buffer = ByteArray(DownloadDefaults.BUFFER_SIZE)
        response.body.byteStream().use { input ->
            FileChannel.open(tmpFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use { channel ->
                var pos = start
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    val bb = ByteBuffer.wrap(buffer, 0, read)
                    while (bb.hasRemaining()) {
                        val count = channel.write(bb, pos)
                        if (count <= 0) throw IOException("FileChannel write returned $count")
                        pos += count
                        written += count
                    }
                }
            }
        }
        return written
    }

    internal data class RangeMeta(
        val length: Long,
        val acceptRanges: Boolean,
        val ifRangeToken: String?,
    )

    internal data class RangePart(
        val index: Int,
        val start: Long,
        val end: Long,
    )

}
