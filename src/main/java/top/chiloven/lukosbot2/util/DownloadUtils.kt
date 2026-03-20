package top.chiloven.lukosbot2.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.logging.log4j.LogManager
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.util.concurrent.Coroutines
import top.chiloven.lukosbot2.util.spring.SpringBeans
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * Utils for downloading files.
 *
 * @author Chiloven945
 */
object DownloadUtils {

    private val log = LogManager.getLogger(DownloadUtils::class.java)

    const val DEFAULT_MAX_CONCURRENT_FILES: Int = 8
    const val DEFAULT_CHUNK_THREADS: Int = 4
    const val DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES: Long = 8L * 1024 * 1024
    const val DEFAULT_MIN_PART_SIZE_BYTES: Long = 2L * 1024 * 1024
    const val DEFAULT_MAX_RETRIES: Int = 3
    const val DEFAULT_RETRY_BASE_DELAY_MS: Long = 350
    const val DEFAULT_RETRY_MAX_DELAY_MS: Long = 8_000
    const val DEFAULT_RETRY_AFTER_CAP_MS: Long = 30_000
    const val DEFAULT_PROGRESS_LOG_INTERVAL_MS: Long = 1_000

    private const val BUF_SIZE: Int = 64 * 1024

    @Volatile
    private var cachedClient: OkHttpClient? = null

    @Volatile
    private var cachedKey: String? = null

    private fun proxyOrNull(): ProxyConfigProp? = try {
        SpringBeans.getBean(ProxyConfigProp::class.java)
    } catch (_: Throwable) {
        null
    }

    private fun proxyKey(proxy: ProxyConfigProp): String = listOf(
        proxy.isEnabled,
        proxy.type,
        proxy.host,
        proxy.port,
        proxy.username,
        proxy.password,
        proxy.nonProxyHostsList?.joinToString("|")
    ).joinToString("#")

    private val client: OkHttpClient
        get() {
            val proxy = proxyOrNull()
            val key = if (proxy != null && proxy.isEnabled) proxyKey(proxy) else "NO_PROXY"

            cachedClient?.let { existing ->
                if (cachedKey == key) return existing
            }

            synchronized(this) {
                cachedClient?.let { existing ->
                    if (cachedKey == key) return existing
                }

                val builder = OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)

                if (proxy != null && proxy.isEnabled) {
                    proxy.applyTo(builder)
                }

                return builder.build().also {
                    cachedClient = it
                    cachedKey = key
                }
            }
        }

    @JvmStatic
    fun resolveUrl(server: URI, pathOrUrl: String): URI {
        val path = pathOrUrl.trim()
        if (path.startsWith("http://") || path.startsWith("https://")) return URI.create(path)
        if (path.startsWith('/')) return server.resolve(path)
        return server.resolve("/$path")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadAllToDir(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
    ): BatchResult {
        Files.createDirectories(dir)
        if (items.isNullOrEmpty()) return BatchResult(0, emptyList())

        var ok = 0
        val failed = mutableListOf<String>()

        log.debug("[DL-BATCH] Downloading {} items to {}", items.size, dir)
        items.forEach { log.debug("[DL-BATCH] item={}", it) }

        for (item in items) {
            if (item == null) continue
            val name = item.name.trim()
            val url = item.url

            if (name.isEmpty()) {
                failed += "file"
                continue
            }

            try {
                downloadToDir(url, dir, name, headers, timeoutMs)
                ok++
            } catch (ex: Exception) {
                failed += name
                log.warn("[DL-BATCH] Download failed: name={}, url={}, err={}", name, url, ex.toString())
            }
        }

        log.debug("[DL-BATCH] Done: ok={}, failed={}", ok, failed.size)
        return BatchResult(ok, failed)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadAllToDirConcurrent(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxConcurrentFiles: Int,
    ): BatchResult =
        downloadAllToDirConcurrent(items, dir, headers, timeoutMs, maxConcurrentFiles, 1, DEFAULT_MAX_RETRIES)

    @JvmStatic
    @Throws(IOException::class)
    fun downloadAllToDirConcurrent(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxConcurrentFiles: Int,
        chunkThreadsPerFile: Int,
    ): BatchResult = downloadAllToDirConcurrent(
        items,
        dir,
        headers,
        timeoutMs,
        maxConcurrentFiles,
        chunkThreadsPerFile,
        DEFAULT_MAX_RETRIES
    )

    @JvmStatic
    @Throws(IOException::class)
    fun downloadAllToDirConcurrent(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxConcurrentFiles: Int,
        chunkThreadsPerFile: Int,
        maxRetries: Int,
    ): BatchResult {
        Files.createDirectories(dir)
        if (items.isNullOrEmpty()) return BatchResult(0, emptyList())

        val maxConc = max(1, maxConcurrentFiles)
        val chunkThreads = max(1, chunkThreadsPerFile)
        val retries = max(0, maxRetries)
        val ok = AtomicInteger(0)
        val failed = ConcurrentLinkedQueue<String>()

        log.debug(
            "[DL-BATCH] Concurrent downloading {} items to {}, maxConc={}, chunkThreadsPerFile={}, maxRetries={}",
            items.size,
            dir,
            maxConc,
            chunkThreads,
            retries
        )

        Coroutines.runBlockingIo {
            Coroutines.forEachLimited(items.filterNotNull(), maxConc) { item ->
                val name = item.name.trim()
                val url = item.url

                if (name.isEmpty()) {
                    failed += "file"
                    return@forEachLimited
                }

                try {
                    val startNs = System.nanoTime()

                    if (chunkThreads > 1) {
                        downloadToDirFast(url, dir, name, headers, timeoutMs, chunkThreads, retries)
                    } else {
                        downloadToDir(url, dir, name, headers, timeoutMs, retries)
                    }

                    val costMs = (System.nanoTime() - startNs) / 1_000_000
                    log.debug("[DL-BATCH] OK name={}, url={}, cost={}ms", name, url, costMs)
                    ok.incrementAndGet()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    failed += name
                    log.warn("[DL-BATCH] Download interrupted: name={}, url={}", name, url)
                } catch (ex: Exception) {
                    failed += name
                    log.warn("[DL-BATCH] Download failed: name={}, url={}, err={}", name, url, ex.toString())
                }
            }
        }

        log.debug("[DL-BATCH] Done: ok={}, failed={}", ok.get(), failed.size)
        return BatchResult(ok.get(), failed.toList())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadNamedUrlsToDirConcurrent(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
    ): BatchResult = downloadNamedUrlsToDirConcurrent(
        items,
        dir,
        headers,
        timeoutMs,
        DEFAULT_MAX_CONCURRENT_FILES,
        DEFAULT_CHUNK_THREADS,
        DEFAULT_MAX_RETRIES
    )

    @JvmStatic
    @Throws(IOException::class)
    fun downloadNamedUrlsToDirConcurrent(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxConcurrentFiles: Int,
        chunkThreadsPerFile: Int,
        maxRetries: Int,
    ): BatchResult {
        Files.createDirectories(dir)
        if (items.isNullOrEmpty()) return BatchResult(0, emptyList())

        val maxConc = max(1, maxConcurrentFiles)
        val chunkThreads = max(1, chunkThreadsPerFile)
        val retries = max(0, maxRetries)
        val ok = AtomicInteger(0)
        val failed = ConcurrentLinkedQueue<String>()
        val usedNames = Collections.synchronizedSet(mutableSetOf<String>())

        log.debug(
            "[DL-BATCH-NAMED] Concurrent downloading {} items to {}, maxConc={}, chunkThreadsPerFile={}, maxRetries={}",
            items.size,
            dir,
            maxConc,
            chunkThreads,
            retries
        )

        Coroutines.runBlockingIo {
            Coroutines.forEachLimited(items.filterNotNull(), maxConc) { item ->
                val entryName = item.name.trim()
                val url = item.url

                if (entryName.isEmpty()) {
                    failed += "file"
                    return@forEachLimited
                }

                try {
                    val uniqueEntryName = uniqueEntryName(entryName, usedNames)
                    val target = resolveNamedTarget(dir, uniqueEntryName)
                    target.parent?.let(Files::createDirectories)

                    val startNs = System.nanoTime()
                    if (chunkThreads > 1) {
                        downloadToFileFast(
                            url,
                            target,
                            headers,
                            timeoutMs,
                            chunkThreads,
                            DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
                            DEFAULT_MIN_PART_SIZE_BYTES,
                            retries
                        )
                    } else {
                        downloadToFile(url, target, headers, timeoutMs, retries)
                    }

                    val costMs = (System.nanoTime() - startNs) / 1_000_000
                    log.debug("[DL-BATCH-NAMED] OK entry={}, url={}, cost={}ms", uniqueEntryName, url, costMs)
                    ok.incrementAndGet()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    failed += entryName
                    log.warn("[DL-BATCH-NAMED] Download interrupted: entry={}, url={}", entryName, url)
                } catch (ex: Exception) {
                    failed += entryName
                    log.warn(
                        "[DL-BATCH-NAMED] Download failed: entry={}, url={}, err={}",
                        entryName,
                        url,
                        ex.toString()
                    )
                }
            }
        }

        log.debug("[DL-BATCH-NAMED] Done: ok={}, failed={}", ok.get(), failed.size)
        return BatchResult(ok.get(), failed.toList())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadToFile(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
    ) {
        downloadToFile(url, targetFile, headers, timeoutMs, DEFAULT_MAX_RETRIES)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadToFile(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxRetries: Int,
    ) {
        val retries = max(0, maxRetries)
        val maxAttempts = 1 + retries

        targetFile.parent?.let(Files::createDirectories)
        val tmp = tempPathOf(targetFile)

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
                    if (log.isDebugEnabled) {
                        log.debug(
                            "[DL] start attempt {}/{} url={} -> {}{}",
                            attempt,
                            maxAttempts,
                            url,
                            targetFile,
                            if (resumeFrom > 0) " (resume@$resumeFrom)" else ""
                        )
                    }

                    var finalResumeFrom = resumeFrom
                    var usedRange = finalResumeFrom > 0

                    while (true) {
                        var restart = false
                        execute(
                            buildRequest(url, headers, timeoutMs, usedRange, finalResumeFrom, ifRangeToken, null),
                            timeoutMs
                        ).use { response ->
                            val code = response.code
                            pickIfRangeToken(response.headers)?.let { ifRangeToken = it }
                            debugResponseSummary(url, code, response.headers, usedRange, finalResumeFrom)

                            if (usedRange && code == 416) {
                                log.debug(
                                    "[DL] HTTP 416 for resume range, restart from scratch: url={}, tmp={}",
                                    url,
                                    tmp
                                )
                                safeDelete(tmp)
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
                                    code,
                                    parseRetryAfterMs(response.header("Retry-After")),
                                    "HTTP $code"
                                )
                            }

                            val totalSize = guessTotalSize(code, response.headers)
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
                            val buffer = ByteArray(BUF_SIZE)

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
                                            val intervalNs =
                                                Duration.ofMillis(DEFAULT_PROGRESS_LOG_INTERVAL_MS).toNanos()
                                            if (now - lastLogNs >= intervalNs) {
                                                val deltaBytes = pos - lastLogBytes
                                                val deltaNs = now - lastLogNs
                                                val pct = if (totalSize > 0) String.format(
                                                    Locale.ROOT,
                                                    "%.1f%%",
                                                    pos * 100.0 / totalSize
                                                ) else "?"

                                                log.debug(
                                                    "[DL] progress {} -> {}: {} / {} ({}), instSpeed={}",
                                                    url,
                                                    targetFile,
                                                    formatBytes(pos),
                                                    if (totalSize > 0) formatBytes(totalSize) else "?",
                                                    pct,
                                                    formatSpeed(deltaBytes, deltaNs)
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
                                        formatBytes(pos),
                                        attemptMs,
                                        formatSpeed(pos, System.nanoTime() - attemptStartNs)
                                    )
                                }
                            }
                        }

                        if (restart) continue

                        moveReplace(tmp, targetFile)
                        success = true

                        val totalMs = (System.nanoTime() - totalStartNs) / 1_000_000
                        val finalSize = runCatching { Files.size(targetFile) }.getOrDefault(-1L)
                        log.debug(
                            "[DL] success url={} -> {}, finalSize={}, totalCost={}ms, totalAvgSpeed={}",
                            url,
                            targetFile,
                            if (finalSize >= 0) formatBytes(finalSize) else "?",
                            totalMs,
                            if (finalSize >= 0) formatSpeed(finalSize, System.nanoTime() - totalStartNs) else "?"
                        )
                        return
                    }
                } catch (e: IOException) {
                    val willRetry = attempt < maxAttempts && isRetryableException(e)
                    val delayMs = if (willRetry) computeRetryDelayMs(e, attempt) else 0L

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
                        sleepMs(delayMs)
                        continue
                    }

                    log.warn("[DL] failed (no more retries): url={}, target={}, err={}", url, targetFile, e.toString())
                    throw e
                }
            }

            throw IOException("Download failed unexpectedly (should not reach here)")
        } finally {
            if (!success) safeDelete(tmp)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadToDir(
        url: URI,
        dir: Path,
        fileName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
    ): Path = downloadToDir(url, dir, fileName, headers, timeoutMs, DEFAULT_MAX_RETRIES)

    @JvmStatic
    @Throws(IOException::class)
    fun downloadToDir(
        url: URI,
        dir: Path,
        fileName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxRetries: Int,
    ): Path {
        Files.createDirectories(dir)
        val target = dir.resolve(sanitizeFileName(fileName))
        downloadToFile(url, target, headers, timeoutMs, maxRetries)
        return target
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadToFileFast(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
    ) {
        downloadToFileFast(
            url,
            targetFile,
            headers,
            timeoutMs,
            DEFAULT_CHUNK_THREADS,
            DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
            DEFAULT_MIN_PART_SIZE_BYTES,
            DEFAULT_MAX_RETRIES
        )
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadToFileFast(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        chunkThreads: Int,
        minSizeForChunking: Long,
        minPartSizeBytes: Long,
    ) {
        downloadToFileFast(
            url,
            targetFile,
            headers,
            timeoutMs,
            chunkThreads,
            minSizeForChunking,
            minPartSizeBytes,
            DEFAULT_MAX_RETRIES
        )
    }

    @JvmStatic
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
            downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
            return
        }

        val probeStartNs = System.nanoTime()
        val meta = try {
            probeRangeMeta(url, headers, timeoutMs)
        } catch (ex: Exception) {
            log.debug("[DL-FAST] Range probe failed, fallback to single: url={}, err={}", url, ex.toString())
            downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
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
            downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
            return
        }

        val total = meta.length
        val minPart = max(256 * 1024L, minPartSizeBytes)
        val partsBySize = min(Int.MAX_VALUE.toLong(), max(1L, total / minPart)).toInt()
        val parts = max(2, min(threads, partsBySize))

        targetFile.parent?.let(Files::createDirectories)
        val tmp = tempPathOf(targetFile)
        var chunkOk = false
        val fileStartNs = System.nanoTime()

        try {
            safeDelete(tmp)
            RandomAccessFile(tmp.toFile(), "rw").use { it.setLength(total) }

            val partSize = (total + parts - 1) / parts
            val tasks = mutableListOf<Callable<Void>>()
            var actualParts = 0

            for (i in 0 until parts) {
                val start = i * partSize
                val end = min(total - 1, start + partSize - 1)
                if (start > end) break

                val partIndex = i + 1
                actualParts++
                tasks += Callable {
                    downloadRangeToFile(
                        url,
                        tmp,
                        start,
                        end,
                        headers,
                        timeoutMs,
                        maxRetries,
                        partIndex,
                        meta.ifRangeToken
                    )
                    null
                }
            }

            log.debug(
                "[DL-FAST] plan url={}, total={}, parts={}, partSize~={}, tmp={}",
                url,
                formatBytes(total),
                actualParts,
                formatBytes(partSize),
                tmp
            )

            Coroutines.runBlockingIo {
                coroutineScope {
                    tasks.map { task ->
                        async(Dispatchers.IO) {
                            task.call()
                        }
                    }.awaitAll()
                }
            }

            moveReplace(tmp, targetFile)
            chunkOk = true

            val costMs = (System.nanoTime() - fileStartNs) / 1_000_000
            log.debug(
                "[DL-FAST] success url={} -> {}, bytes={}, cost={}ms, avgSpeed={}",
                url,
                targetFile,
                formatBytes(total),
                costMs,
                formatSpeed(total, System.nanoTime() - fileStartNs)
            )
        } catch (ex: IOException) {
            val costMs = (System.nanoTime() - fileStartNs) / 1_000_000
            log.warn(
                "[DL-FAST] chunked download failed after {}ms, will fallback to single: url={}, err={}",
                costMs,
                url,
                ex.toString()
            )
        } finally {
            if (!chunkOk) safeDelete(tmp)
        }

        if (!chunkOk) {
            downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadToDirFast(
        url: URI,
        dir: Path,
        fileName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
        chunkThreads: Int,
    ): Path = downloadToDirFast(url, dir, fileName, headers, timeoutMs, chunkThreads, DEFAULT_MAX_RETRIES)

    @JvmStatic
    @Throws(IOException::class)
    fun downloadToDirFast(
        url: URI,
        dir: Path,
        fileName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
        chunkThreads: Int,
        maxRetries: Int,
    ): Path {
        Files.createDirectories(dir)
        val target = dir.resolve(sanitizeFileName(fileName))
        downloadToFileFast(
            url,
            target,
            headers,
            timeoutMs,
            chunkThreads,
            DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
            DEFAULT_MIN_PART_SIZE_BYTES,
            maxRetries
        )
        return target
    }

    @JvmStatic
    fun sanitizeFileName(name: String?): String {
        var sanitized = name?.trim().orEmpty()
        sanitized = sanitized.replace('\\', '_').replace('/', '_')
        sanitized = sanitized.replace(Regex("[<>:\"|?*]"), "_")
        sanitized = sanitized.replace(Regex("\\p{Cntrl}"), "_")
        if (sanitized.isBlank()) sanitized = "file"
        return sanitized
    }

    @Throws(IOException::class)
    private fun downloadRangeToFile(
        url: URI,
        tmpFile: Path,
        start: Long,
        end: Long,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxRetries: Int,
        partIndex: Int,
        ifRangeToken: String?,
    ) {
        val expected = end - start + 1
        if (expected <= 0) return

        val retries = max(0, maxRetries)
        val maxAttempts = 1 + retries

        for (attempt in 1..maxAttempts) {
            val startedNs = System.nanoTime()
            try {
                if (log.isDebugEnabled) {
                    log.debug(
                        "[DL-PART] start part#{} range {}-{} ({}), attempt {}/{} url={}",
                        partIndex,
                        start,
                        end,
                        formatBytes(expected),
                        attempt,
                        maxAttempts,
                        url
                    )
                }

                execute(
                    buildRequest(url, headers, timeoutMs, true, start, ifRangeToken, end),
                    timeoutMs
                ).use { response ->
                    val code = response.code
                    debugResponseSummary(url, code, response.headers, true, start)

                    if (code >= 400) {
                        throw HttpStatusException(code, parseRetryAfterMs(response.header("Retry-After")), "HTTP $code")
                    }
                    if (code != 206) {
                        throw IOException("Expected HTTP 206, got HTTP $code")
                    }

                    var written = 0L
                    val buffer = ByteArray(BUF_SIZE)
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

                    if (written != expected) {
                        throw IOException("Range bytes mismatch: expected=$expected, got=$written, range=$start-$end")
                    }
                }

                val elapsedNs = System.nanoTime() - startedNs
                log.debug(
                    "[DL-PART] done part#{} range {}-{}, bytes={}, cost={}ms, avgSpeed={}",
                    partIndex,
                    start,
                    end,
                    formatBytes(expected),
                    elapsedNs / 1_000_000,
                    formatSpeed(expected, elapsedNs)
                )
                return
            } catch (e: IOException) {
                val willRetry = attempt < maxAttempts && isRetryableException(e)
                val delayMs = if (willRetry) computeRetryDelayMs(e, attempt) else 0L
                if (willRetry) {
                    log.warn(
                        "[DL-PART] part#{} attempt {}/{} failed, retry in {}ms: url={}, err={}",
                        partIndex,
                        attempt,
                        maxAttempts,
                        delayMs,
                        url,
                        e.toString()
                    )
                    sleepMs(delayMs)
                    continue
                }

                log.warn("[DL-PART] part#{} failed (no more retries): url={}, err={}", partIndex, url, e.toString())
                throw e
            }
        }

        throw IOException("Part download failed unexpectedly (should not reach here)")
    }

    @Throws(IOException::class)
    private fun probeRangeMeta(url: URI, headers: Map<String, String>?, timeoutMs: Int): RangeMeta {
        try {
            execute(
                buildRequest(url, headers, timeoutMs, false, null, null, null, method = "HEAD"),
                timeoutMs
            ).use { response ->
                val code = response.code
                debugResponseSummary(url, code, response.headers, false, 0)
                if (code < 400) {
                    val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    val accept = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    val token = pickIfRangeToken(response.headers)
                    if (accept && len > 0) {
                        log.debug("[DL-PROBE] HEAD says acceptRanges=true len={} url={}", formatBytes(len), url)
                        return RangeMeta(len, true, token)
                    }

                    log.debug(
                        "[DL-PROBE] HEAD insufficient (acceptRanges={}, len={}) url={}",
                        accept,
                        if (len > 0) formatBytes(len) else "unknown",
                        url
                    )
                }
            }
        } catch (_: Exception) {
            log.debug("[DL-PROBE] HEAD failed, will try Range probe: url={}", url)
        }

        execute(buildRequest(url, headers, timeoutMs, true, 0L, null, 0L), timeoutMs).use { response ->
            val code = response.code
            debugResponseSummary(url, code, response.headers, true, 0)
            if (code == 206) {
                val total = response.header("Content-Range")?.let(::parseTotalFromContentRange) ?: -1L
                val token = pickIfRangeToken(response.headers)
                if (total > 0) {
                    log.debug("[DL-PROBE] Range probe OK: acceptRanges=true total={} url={}", formatBytes(total), url)
                    return RangeMeta(total, true, token)
                }

                log.debug("[DL-PROBE] Range probe OK but total unknown: url={}", url)
                return RangeMeta(-1L, true, token)
            }

            log.debug("[DL-PROBE] Range probe not supported (code={}): url={}", code, url)
            return RangeMeta(-1L, false, null)
        }
    }

    @Throws(IOException::class)
    private fun execute(request: Request, timeoutMs: Int): Response =
        client.newBuilder()
            .callTimeout(max(1, timeoutMs).toLong(), TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
            .execute()

    private fun buildRequest(
        url: URI,
        headers: Map<String, String>?,
        timeoutMs: Int,
        forceIdentityEncoding: Boolean,
        rangeStart: Long?,
        ifRangeToken: String?,
        rangeEnd: Long?,
        method: String = "GET",
    ): Request {
        val builder = Request.Builder()
            .url(url.toString())
            .header("User-Agent", Constants.UA)
            .header("Accept", "*/*")
            .method(method, null)

        if (forceIdentityEncoding) {
            builder.header("Accept-Encoding", "identity")
        }

        headers?.forEach { (key, value) ->
            if (key.isNotBlank()) {
                builder.header(key, value)
            }
        }

        if (rangeStart != null) {
            val rangeValue = if (rangeEnd != null) "bytes=$rangeStart-$rangeEnd" else "bytes=$rangeStart-"
            builder.header("Range", rangeValue)
            if (!ifRangeToken.isNullOrBlank()) {
                builder.header("If-Range", ifRangeToken)
            }
        }

        return builder.build()
    }

    @Throws(IOException::class)
    private fun resolveNamedTarget(dir: Path, entryName: String): Path {
        val safe = normalizeRelativeEntryName(entryName)
        val base = dir.toAbsolutePath().normalize()
        val target = base.resolve(safe).normalize()
        if (!target.startsWith(base)) {
            throw IOException("Illegal target path: $entryName")
        }
        return target
    }

    @Throws(IOException::class)
    private fun uniqueEntryName(entryName: String, usedNames: MutableSet<String>): String {
        val safe = normalizeRelativeEntryName(entryName)
        synchronized(usedNames) {
            if (usedNames.add(safe)) return safe

            val slash = safe.lastIndexOf('/')
            val dirPart = if (slash >= 0) safe.substring(0, slash + 1) else ""
            val filePart = if (slash >= 0) safe.substring(slash + 1) else safe
            val dot = filePart.lastIndexOf('.')
            val base = if (dot > 0) filePart.substring(0, dot) else filePart
            val ext = if (dot > 0) filePart.substring(dot) else ""

            var index = 2
            while (true) {
                val candidate = "$dirPart$base ($index)$ext"
                if (usedNames.add(candidate)) return candidate
                index++
            }
        }
    }

    @Throws(IOException::class)
    private fun normalizeRelativeEntryName(entryName: String?): String {
        var normalized = entryName?.trim().orEmpty().replace('\\', '/')
        while (normalized.startsWith('/')) {
            normalized = normalized.substring(1)
        }
        if (normalized.isBlank()) throw IOException("Empty entry name")

        normalized = Paths.get(normalized).normalize().toString().replace('\\', '/')
        if (normalized.isBlank() || normalized == "." || normalized.startsWith("..") || normalized.contains("/../")) {
            throw IOException("Illegal entry name: $entryName")
        }
        return normalized
    }

    @Throws(IOException::class)
    private fun moveReplace(tmp: Path, targetFile: Path) {
        try {
            Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun debugResponseSummary(url: URI, code: Int, headers: Headers, askedRange: Boolean, rangeStart: Long) {
        if (!log.isDebugEnabled) return

        log.debug(
            "[DL-HTTP] url={} code={} askedRange={}{} contentLength={} contentRange={} acceptRanges={} contentType={} etag={} lastModified={} retryAfter={}",
            url,
            code,
            askedRange,
            if (askedRange) "(start=$rangeStart)" else "",
            headers["Content-Length"] ?: "-",
            headers["Content-Range"] ?: "-",
            headers["Accept-Ranges"] ?: "-",
            headers["Content-Type"] ?: "-",
            headers["ETag"] ?: "-",
            headers["Last-Modified"] ?: "-",
            headers["Retry-After"] ?: "-"
        )
    }

    private fun guessTotalSize(code: Int, headers: Headers): Long =
        if (code == 206) headers["Content-Range"]?.let(::parseTotalFromContentRange) ?: -1L
        else headers["Content-Length"]?.toLongOrNull() ?: -1L

    private fun pickIfRangeToken(headers: Headers): String? =
        headers["ETag"]?.takeUnless(String::isBlank)
            ?: headers["Last-Modified"]?.takeUnless(String::isBlank)

    private fun parseTotalFromContentRange(value: String?): Long {
        if (value.isNullOrBlank()) return -1L
        val slash = value.lastIndexOf('/')
        if (slash < 0 || slash + 1 >= value.length) return -1L
        val total = value.substring(slash + 1).trim()
        if (total.isEmpty() || total == "*") return -1L
        return total.toLongOrNull() ?: -1L
    }

    private fun isRetryableStatus(code: Int): Boolean = code == 408 || code == 429 || code in 500..599

    private fun isRetryableException(error: IOException): Boolean = when (error) {
        is HttpStatusException -> isRetryableStatus(error.statusCode)
        is FileSystemException -> false
        else -> true
    }

    private fun computeRetryDelayMs(error: IOException, attemptIndex: Int): Long {
        val exp = 1L shl min(20, max(0, attemptIndex - 1))
        var delay = min(DEFAULT_RETRY_MAX_DELAY_MS, DEFAULT_RETRY_BASE_DELAY_MS * exp)

        if (error is HttpStatusException && error.retryAfterMs != null && error.retryAfterMs > 0) {
            delay = max(delay, min(DEFAULT_RETRY_AFTER_CAP_MS, error.retryAfterMs))
        }

        val jitter = ThreadLocalRandom.current().nextLong(0, max(1L, delay / 3))
        return min(DEFAULT_RETRY_MAX_DELAY_MS, delay + jitter)
    }

    private fun parseRetryAfterMs(value: String?): Long? {
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
    private fun sleepMs(ms: Long) {
        if (ms <= 0) return
        try {
            Thread.sleep(ms)
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Sleep interrupted", ie)
        }
    }

    private fun safeDelete(path: Path) {
        runCatching { Files.deleteIfExists(path) }
    }

    private fun tempPathOf(targetFile: Path): Path =
        targetFile.parent?.resolve(targetFile.fileName.toString() + ".part")
            ?: Paths.get(targetFile.fileName.toString() + ".part")

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "?"
        var value = bytes.toDouble()
        val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return if (index == 0) String.format(Locale.ROOT, "%d %s", bytes, units[index])
        else String.format(Locale.ROOT, "%.2f %s", value, units[index])
    }

    private fun formatSpeed(bytes: Long, elapsedNs: Long): String {
        if (bytes < 0 || elapsedNs <= 0) return "?"
        val sec = elapsedNs / 1_000_000_000.0
        return formatBytes((bytes / sec).toLong()) + "/s"
    }

    private class HttpStatusException(
        val statusCode: Int,
        val retryAfterMs: Long?,
        message: String,
    ) : IOException(message)

    private class RangeMeta(
        val length: Long,
        val acceptRanges: Boolean,
        val ifRangeToken: String?,
    )

    class NamedUrl(
        val name: String,
        val url: URI,
    ) {

        fun name(): String = name
        fun url(): URI = url

        override fun toString(): String = "NamedUrl(name=$name, url=$url)"
        override fun equals(other: Any?): Boolean =
            this === other || (other is NamedUrl && name == other.name && url == other.url)

        override fun hashCode(): Int = 31 * name.hashCode() + url.hashCode()

    }

    class BatchResult(
        val ok: Int,
        val failed: List<String>,
    ) {

        fun ok(): Int = ok
        fun failed(): List<String> = failed

        override fun toString(): String = "BatchResult(ok=$ok, failed=$failed)"
        override fun equals(other: Any?): Boolean =
            this === other || (other is BatchResult && ok == other.ok && failed == other.failed)

        override fun hashCode(): Int = 31 * ok + failed.hashCode()

    }

}
