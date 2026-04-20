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
import top.chiloven.lukosbot2.util.DownloadUtils.downloadAllToDir
import top.chiloven.lukosbot2.util.DownloadUtils.downloadAllToDirConcurrent
import top.chiloven.lukosbot2.util.DownloadUtils.downloadNamedUrlsToDirConcurrent
import top.chiloven.lukosbot2.util.DownloadUtils.downloadToDir
import top.chiloven.lukosbot2.util.DownloadUtils.downloadToDirFast
import top.chiloven.lukosbot2.util.DownloadUtils.downloadToFile
import top.chiloven.lukosbot2.util.DownloadUtils.downloadToFileFast
import top.chiloven.lukosbot2.util.DownloadUtils.sanitizeFileName
import top.chiloven.lukosbot2.util.concurrent.Coroutines
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
 * Resilient file-download helper built on top of OkHttp.
 *
 * This utility centralizes the download flow that is reused across the project:
 *
 * 1. Build HTTP requests with shared headers and proxy-aware client reuse.
 * 2. Download a single file to a target path or target directory.
 * 3. Optionally resume interrupted downloads when the server supports it.
 * 4. Optionally split large files into ranged chunks for faster downloads.
 * 5. Batch-download multiple resources with bounded concurrency.
 *
 * ## Scope and design goals
 *
 * `DownloadUtils` intentionally focuses on “download a remote resource onto local disk” and keeps
 * the implementation opinionated:
 *
 * - Only **HTTP GET** and **HTTP HEAD** are used.
 * - Downloads are written through `*.part` temporary files and moved into place only after success.
 * - Failed downloads are retried with exponential backoff and jitter.
 * - Batch helpers return a lightweight summary instead of throwing on the first failed item.
 * - Proxy configuration is auto-discovered from Spring when available.
 *
 * ## Public API overview
 *
 * The object exposes two groups of helpers:
 *
 * - **Single-file helpers** such as [downloadToFile], [downloadToDir], [downloadToFileFast], and
 *   [downloadToDirFast].
 * - **Batch helpers** such as [downloadAllToDir], [downloadAllToDirConcurrent], and
 *   [downloadNamedUrlsToDirConcurrent].
 *
 * `downloadAllToDir*` treats each input name as a plain file name inside the target directory,
 * while [downloadNamedUrlsToDirConcurrent] preserves relative subdirectories from the provided
 * entry name and auto-deduplicates collisions.
 *
 * ## Chunked download behavior
 *
 * “Fast” helpers probe whether the remote server supports byte ranges. When range requests are
 * available and the file is large enough, the payload is downloaded in parallel chunks and merged
 * into a single target file. Otherwise, the implementation falls back to the normal single-stream
 * downloader automatically.
 *
 * ## Connection reuse
 *
 * The object caches a single [OkHttpClient] instance and rebuilds it only when the effective proxy
 * configuration changes. This keeps connection pooling effective while still honoring updated proxy
 * settings.
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
    private val clientCache = OkHttpUtils.ProxyAwareOkHttpClientCache(connectTimeoutMs = 20_000)

    private const val INVALID_BATCH_NAME = "file"

    private enum class BatchNamingMode(val logTag: String) {
        FLAT_FILES("[DL-BATCH]"),
        RELATIVE_PATHS("[DL-BATCH-NAMED]")
    }

    private data class BatchDownloadOptions(
        val maxConcurrentFiles: Int = 1,
        val chunkThreadsPerFile: Int = 1,
        val maxRetries: Int = DEFAULT_MAX_RETRIES,
    ) {

        val normalizedMaxConcurrentFiles: Int = max(1, maxConcurrentFiles)
        val normalizedChunkThreadsPerFile: Int = max(1, chunkThreadsPerFile)
        val normalizedMaxRetries: Int = max(0, maxRetries)

    }

    private val client: OkHttpClient
        get() = clientCache.client

    /**
     * Download a list of files into a directory sequentially.
     *
     * Each item name is treated as a plain file name and sanitized with [sanitizeFileName] before
     * the file is written under [dir]. When one item fails, the batch continues and the failed item
     * name is recorded in the returned [BatchResult].
     *
     * @param items resources to download; `null` items are ignored
     * @param dir destination directory
     * @param headers optional extra request headers applied to every request
     * @param timeoutMs per-request timeout in milliseconds
     * @return batch summary containing succeeded count and failed item names
     * @throws IOException if the destination directory cannot be created
     */
    @JvmStatic
    @Throws(IOException::class)
    fun downloadAllToDir(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
    ): BatchResult = downloadBatch(
        items = items,
        dir = dir,
        headers = headers,
        timeoutMs = timeoutMs,
        namingMode = BatchNamingMode.FLAT_FILES,
        options = BatchDownloadOptions()
    )

    /**
     * Download a list of files into a directory with bounded concurrency.
     *
     * Each item name is treated as a plain file name inside [dir]. When [chunkThreadsPerFile] is
     * greater than `1`, each file may use ranged chunk downloading via [downloadToDirFast].
     * Otherwise, the normal single-stream downloader is used.
     *
     * @param items resources to download; `null` items are ignored
     * @param dir destination directory
     * @param headers optional extra request headers applied to every request
     * @param timeoutMs per-request timeout in milliseconds
     * @param maxConcurrentFiles maximum number of files downloaded at the same time
     * @param chunkThreadsPerFile chunk workers used per file when chunk download is enabled
     * @param maxRetries retry count for each individual file request
     * @return batch summary containing succeeded count and failed item names
     * @throws IOException if the destination directory cannot be created
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun downloadAllToDirConcurrent(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxConcurrentFiles: Int = DEFAULT_MAX_CONCURRENT_FILES,
        chunkThreadsPerFile: Int = 1,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
    ): BatchResult = downloadBatch(
        items = items,
        dir = dir,
        headers = headers,
        timeoutMs = timeoutMs,
        namingMode = BatchNamingMode.FLAT_FILES,
        options = BatchDownloadOptions(maxConcurrentFiles, chunkThreadsPerFile, maxRetries)
    )

    /**
     * Download a list of named files into a directory while preserving relative paths.
     *
     * Unlike [downloadAllToDirConcurrent], each item name is interpreted as a relative entry path.
     * Nested directories are created automatically, path traversal is rejected, and duplicate entry
     * names are deduplicated by appending ` (2)`, ` (3)`, and so on.
     *
     * @param items resources to download; `null` items are ignored
     * @param dir destination directory
     * @param headers optional extra request headers applied to every request
     * @param timeoutMs per-request timeout in milliseconds
     * @param maxConcurrentFiles maximum number of files downloaded at the same time
     * @param chunkThreadsPerFile chunk workers used per file when chunk download is enabled
     * @param maxRetries retry count for each individual file request
     * @return batch summary containing succeeded count and failed entry names
     * @throws IOException if the destination directory cannot be created
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun downloadNamedUrlsToDirConcurrent(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxConcurrentFiles: Int = DEFAULT_MAX_CONCURRENT_FILES,
        chunkThreadsPerFile: Int = DEFAULT_CHUNK_THREADS,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
    ): BatchResult = downloadBatch(
        items = items,
        dir = dir,
        headers = headers,
        timeoutMs = timeoutMs,
        namingMode = BatchNamingMode.RELATIVE_PATHS,
        options = BatchDownloadOptions(maxConcurrentFiles, chunkThreadsPerFile, maxRetries)
    )

    @Throws(IOException::class)
    private fun downloadBatch(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        namingMode: BatchNamingMode,
        options: BatchDownloadOptions,
    ): BatchResult {
        Files.createDirectories(dir)

        val actualItems = items.orEmpty().filterNotNull()
        if (actualItems.isEmpty()) return BatchResult(0, emptyList())

        val ok = AtomicInteger(0)
        val failed = ConcurrentLinkedQueue<String>()
        val usedNames = if (namingMode == BatchNamingMode.RELATIVE_PATHS) {
            Collections.synchronizedSet(mutableSetOf<String>())
        } else {
            null
        }

        log.debug(
            "{} Downloading {} items to {}, maxConc={}, chunkThreadsPerFile={}, maxRetries={}",
            namingMode.logTag,
            actualItems.size,
            dir,
            options.normalizedMaxConcurrentFiles,
            options.normalizedChunkThreadsPerFile,
            options.normalizedMaxRetries
        )
        actualItems.forEach { log.debug("{} item={}", namingMode.logTag, it) }

        Coroutines.runBlockingIo {
            Coroutines.forEachLimited(actualItems, options.normalizedMaxConcurrentFiles) { item ->
                val requestedName = item.name.trim()
                if (requestedName.isEmpty()) {
                    failed += INVALID_BATCH_NAME
                    return@forEachLimited
                }

                try {
                    val startNs = System.nanoTime()
                    val loggedName = when (namingMode) {
                        BatchNamingMode.FLAT_FILES -> {
                            downloadBatchItemToFlatDir(
                                url = item.url,
                                dir = dir,
                                fileName = requestedName,
                                headers = headers,
                                timeoutMs = timeoutMs,
                                options = options
                            )
                            requestedName
                        }

                        BatchNamingMode.RELATIVE_PATHS -> {
                            val uniqueEntryName = uniqueEntryName(
                                entryName = requestedName,
                                usedNames = checkNotNull(usedNames)
                            )
                            downloadBatchItemToNamedTarget(
                                url = item.url,
                                dir = dir,
                                entryName = uniqueEntryName,
                                headers = headers,
                                timeoutMs = timeoutMs,
                                options = options
                            )
                            uniqueEntryName
                        }
                    }

                    val costMs = (System.nanoTime() - startNs) / 1_000_000
                    log.debug("{} OK name={}, url={}, cost={}ms", namingMode.logTag, loggedName, item.url, costMs)
                    ok.incrementAndGet()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    failed += requestedName
                    log.warn("{} Download interrupted: name={}, url={}", namingMode.logTag, requestedName, item.url)
                } catch (ex: Exception) {
                    failed += requestedName
                    log.warn(
                        "{} Download failed: name={}, url={}, err={}",
                        namingMode.logTag,
                        requestedName,
                        item.url,
                        ex.toString()
                    )
                }
            }
        }

        log.debug("{} Done: ok={}, failed={}", namingMode.logTag, ok.get(), failed.size)
        return BatchResult(ok.get(), failed.toList())
    }

    @Throws(IOException::class)
    private fun downloadBatchItemToFlatDir(
        url: URI,
        dir: Path,
        fileName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
        options: BatchDownloadOptions,
    ) {
        if (options.normalizedChunkThreadsPerFile > 1) {
            downloadToDirFast(
                url = url,
                dir = dir,
                fileName = fileName,
                headers = headers,
                timeoutMs = timeoutMs,
                chunkThreads = options.normalizedChunkThreadsPerFile,
                maxRetries = options.normalizedMaxRetries
            )
        } else {
            downloadToDir(
                url = url,
                dir = dir,
                fileName = fileName,
                headers = headers,
                timeoutMs = timeoutMs,
                maxRetries = options.normalizedMaxRetries
            )
        }
    }

    @Throws(IOException::class)
    private fun downloadBatchItemToNamedTarget(
        url: URI,
        dir: Path,
        entryName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
        options: BatchDownloadOptions,
    ) {
        val target = resolveNamedTarget(dir, entryName)
        target.parent?.let(Files::createDirectories)

        if (options.normalizedChunkThreadsPerFile > 1) {
            downloadToFileFast(
                url = url,
                targetFile = target,
                headers = headers,
                timeoutMs = timeoutMs,
                chunkThreads = options.normalizedChunkThreadsPerFile,
                minSizeForChunking = DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
                minPartSizeBytes = DEFAULT_MIN_PART_SIZE_BYTES,
                maxRetries = options.normalizedMaxRetries
            )
        } else {
            downloadToFile(
                url = url,
                targetFile = target,
                headers = headers,
                timeoutMs = timeoutMs,
                maxRetries = options.normalizedMaxRetries
            )
        }
    }

    /**
     * Download a single resource into the specified file.
     *
     * The download is written to a sibling `*.part` file first and moved into place only after the
     * request completes successfully. Retry attempts reuse the partial file when possible and may
     * resume from the last written offset.
     *
     * @param url resource URL
     * @param targetFile final target path on disk
     * @param headers optional extra request headers
     * @param timeoutMs per-request timeout in milliseconds
     * @param maxRetries retry count used for transport or retryable HTTP failures
     * @throws IOException if the download ultimately fails
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun downloadToFile(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
    ) {
        val retries = max(0, maxRetries)
        val maxAttempts = 1 + retries

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
                                                    displayBytes(pos),
                                                    if (totalSize > 0) displayBytes(totalSize) else "?",
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
                                        displayBytes(pos),
                                        attemptMs,
                                        formatSpeed(pos, System.nanoTime() - attemptStartNs)
                                    )
                                }
                            }
                        }

                        if (restart) continue

                        PathUtils.moveReplace(tmp, targetFile)
                        success = true

                        val totalMs = (System.nanoTime() - totalStartNs) / 1_000_000
                        val finalSize = runCatching { Files.size(targetFile) }.getOrDefault(-1L)
                        log.debug(
                            "[DL] success url={} -> {}, finalSize={}, totalCost={}ms, totalAvgSpeed={}",
                            url,
                            targetFile,
                            if (finalSize >= 0) displayBytes(finalSize) else "?",
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
            if (!success) PathUtils.deleteIfExistsQuietly(tmp)
        }
    }

    /**
     * Download a single resource into the specified directory using [fileName] as the target name.
     *
     * The file name is sanitized with [sanitizeFileName] before the target path is resolved.
     *
     * @param url resource URL
     * @param dir destination directory
     * @param fileName desired file name inside [dir]
     * @param headers optional extra request headers
     * @param timeoutMs per-request timeout in milliseconds
     * @param maxRetries retry count used for transport or retryable HTTP failures
     * @return final target path
     * @throws IOException if directory creation or download fails
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun downloadToDir(
        url: URI,
        dir: Path,
        fileName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
    ): Path {
        Files.createDirectories(dir)
        val target = dir.resolve(sanitizeFileName(fileName))
        downloadToFile(url, target, headers, timeoutMs, maxRetries)
        return target
    }

    /**
     * Download a single resource using ranged chunk downloads when beneficial.
     *
     * The method first probes server range support. If chunking is not supported or the payload is
     * too small, it transparently falls back to [downloadToFile].
     *
     * @param url resource URL
     * @param targetFile final target path on disk
     * @param headers optional extra request headers
     * @param timeoutMs per-request timeout in milliseconds
     * @param chunkThreads maximum chunk workers used when range download is enabled
     * @param minSizeForChunking minimum total file size required before chunking is considered
     * @param minPartSizeBytes minimum desired part size used to derive the final part count
     * @param maxRetries retry count used for transport or retryable HTTP failures
     * @throws IOException if the download ultimately fails
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun downloadToFileFast(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        chunkThreads: Int = DEFAULT_CHUNK_THREADS,
        minSizeForChunking: Long = DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
        minPartSizeBytes: Long = DEFAULT_MIN_PART_SIZE_BYTES,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
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
        val tmp = PathUtils.tempSiblingPath(targetFile)
        var chunkOk = false
        val fileStartNs = System.nanoTime()

        try {
            PathUtils.deleteIfExistsQuietly(tmp)
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
                displayBytes(total),
                actualParts,
                displayBytes(partSize),
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

            PathUtils.moveReplace(tmp, targetFile)
            chunkOk = true

            val costMs = (System.nanoTime() - fileStartNs) / 1_000_000
            log.debug(
                "[DL-FAST] success url={} -> {}, bytes={}, cost={}ms, avgSpeed={}",
                url,
                targetFile,
                displayBytes(total),
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
            if (!chunkOk) PathUtils.deleteIfExistsQuietly(tmp)
        }

        if (!chunkOk) {
            downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
        }
    }

    /**
     * Download a single resource into a directory using ranged chunk downloads when beneficial.
     *
     * This is the directory-targeting convenience wrapper over [downloadToFileFast].
     *
     * @param url resource URL
     * @param dir destination directory
     * @param fileName desired file name inside [dir]
     * @param headers optional extra request headers
     * @param timeoutMs per-request timeout in milliseconds
     * @param chunkThreads maximum chunk workers used when range download is enabled
     * @param maxRetries retry count used for transport or retryable HTTP failures
     * @return final target path
     * @throws IOException if directory creation or download fails
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun downloadToDirFast(
        url: URI,
        dir: Path,
        fileName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
        chunkThreads: Int = DEFAULT_CHUNK_THREADS,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
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

    /**
     * Sanitize an arbitrary file name into a filesystem-friendly single-path-segment name.
     *
     * Directory separators, control characters, and characters commonly rejected by desktop file
     * systems are replaced with underscores. Blank inputs fall back to `"file"`.
     *
     * @param name original file name text
     * @return sanitized single-file name
     */
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
                        displayBytes(expected),
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
                    displayBytes(expected),
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
                        log.debug("[DL-PROBE] HEAD says acceptRanges=true len={} url={}", displayBytes(len), url)
                        return RangeMeta(len, true, token)
                    }

                    log.debug(
                        "[DL-PROBE] HEAD insufficient (acceptRanges={}, len={}) url={}",
                        accept,
                        if (len > 0) displayBytes(len) else "unknown",
                        url
                    )
                }
            }
        } catch (_: Exception) {
            log.debug("[DL-PROBE] HEAD failed, will try Range probe: url={}", url)
        }

        execute(
            buildRequest(
                url,
                headers,
                timeoutMs,
                true,
                0L,
                null,
                0L
            ), timeoutMs
        ).use { response ->
            val code = response.code
            debugResponseSummary(url, code, response.headers, true, 0)
            if (code == 206) {
                val total = response.header("Content-Range")?.let(::parseTotalFromContentRange) ?: -1L
                val token = pickIfRangeToken(response.headers)
                if (total > 0) {
                    log.debug("[DL-PROBE] Range probe OK: acceptRanges=true total={} url={}", displayBytes(total), url)
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
        val safe = PathUtils.normalizeRelativeEntryName(entryName)
        val base = dir.toAbsolutePath().normalize()
        val target = base.resolve(safe).normalize()
        if (!target.startsWith(base)) {
            throw IOException("Illegal target path: $entryName")
        }
        return target
    }

    @Throws(IOException::class)
    private fun uniqueEntryName(entryName: String, usedNames: MutableSet<String>): String =
        PathUtils.uniqueRelativeEntryName(entryName, usedNames)

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

    private fun displayBytes(bytes: Long): String =
        if (bytes < 0) "?" else StringUtils.fmtBytes(bytes, 2)

    private fun formatSpeed(bytes: Long, elapsedNs: Long): String {
        if (bytes < 0 || elapsedNs <= 0) return "?"
        val sec = elapsedNs / 1_000_000_000.0
        return displayBytes((bytes / sec).toLong()) + "/s"
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

    /**
     * Pair a target file name (or relative entry name) with a download URL.
     *
     * @property name plain file name for flat downloads, or relative entry path for named batch
     * downloads
     * @property url resource URL
     */
    data class NamedUrl(
        val name: String,
        val url: URI,
    ) {

        fun name(): String = name
        fun url(): URI = url

    }

    /**
     * Summary returned by best-effort batch download helpers.
     *
     * @property ok number of items downloaded successfully
     * @property failed requested names that could not be downloaded
     */
    data class BatchResult(
        val ok: Int,
        val failed: List<String>,
    ) {

        fun ok(): Int = ok
        fun failed(): List<String> = failed

    }

}
