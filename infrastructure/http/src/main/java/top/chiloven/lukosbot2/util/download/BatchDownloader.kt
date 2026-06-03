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

import org.apache.logging.log4j.LogManager
import top.chiloven.lukosbot2.util.DownloadUtils
import top.chiloven.lukosbot2.util.concurrent.Coroutines
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.math.max

internal class BatchDownloader(
    private val singleFileDownloader: SingleFileDownloader,
    private val rangeDownloader: RangeDownloader,
) {

    private val log = LogManager.getLogger(BatchDownloader::class.java)

    @Throws(IOException::class)
    fun download(
        items: List<DownloadUtils.NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        namingMode: BatchNamingMode,
        options: BatchDownloadOptions,
    ): DownloadUtils.BatchResult {
        Files.createDirectories(dir)

        val actualItems = items.orEmpty().filterNotNull()
        if (actualItems.isEmpty()) return DownloadUtils.BatchResult(0, emptyList())

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

        val results = Coroutines.runBlockingIo {
            Coroutines.mapLimited(actualItems, options.normalizedMaxConcurrentFiles) { item ->
                downloadItem(
                    item = item,
                    dir = dir,
                    headers = headers,
                    timeoutMs = timeoutMs,
                    namingMode = namingMode,
                    options = options,
                    usedNames = usedNames
                )
            }
        }

        val ok = results.count { it.success }
        val failed = results
            .filterNot { it.success }
            .map { it.name }

        log.debug("{} Done: ok={}, failed={}", namingMode.logTag, ok, failed.size)
        return DownloadUtils.BatchResult(ok, failed)
    }

    private fun downloadItem(
        item: DownloadUtils.NamedUrl,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        namingMode: BatchNamingMode,
        options: BatchDownloadOptions,
        usedNames: MutableSet<String>?,
    ): BatchItemResult {
        val requestedName = item.name.trim()
        if (requestedName.isEmpty()) {
            return BatchItemResult(DownloadDefaults.INVALID_BATCH_NAME, false)
        }

        return try {
            val startNs = System.nanoTime()
            val loggedName = when (namingMode) {
                BatchNamingMode.FLAT_FILES -> {
                    downloadItemToFlatDir(
                        item = item,
                        dir = dir,
                        headers = headers,
                        timeoutMs = timeoutMs,
                        options = options
                    )
                    requestedName
                }

                BatchNamingMode.RELATIVE_PATHS -> {
                    val uniqueEntryName = DownloadNaming.uniqueRelativeEntryName(
                        entryName = requestedName,
                        usedNames = checkNotNull(usedNames)
                    )
                    downloadItemToNamedTarget(
                        item = item,
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
            BatchItemResult(loggedName, true)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("{} Download interrupted: name={}, url={}", namingMode.logTag, requestedName, item.url)
            BatchItemResult(requestedName, false)
        } catch (ex: Exception) {
            log.warn(
                "{} Download failed: name={}, url={}, err={}",
                namingMode.logTag,
                requestedName,
                item.url,
                ex.toString()
            )
            BatchItemResult(requestedName, false)
        }
    }

    @Throws(IOException::class)
    private fun downloadItemToFlatDir(
        item: DownloadUtils.NamedUrl,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        options: BatchDownloadOptions,
    ) {
        val target = DownloadNaming.resolveFlatTarget(dir, item.name)
        if (options.normalizedChunkThreadsPerFile > 1) {
            rangeDownloader.downloadToFileFast(
                url = item.url,
                targetFile = target,
                headers = headers,
                timeoutMs = timeoutMs,
                chunkThreads = options.normalizedChunkThreadsPerFile,
                minSizeForChunking = DownloadDefaults.DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
                minPartSizeBytes = DownloadDefaults.DEFAULT_MIN_PART_SIZE_BYTES,
                maxRetries = options.normalizedMaxRetries
            )
        } else {
            singleFileDownloader.downloadToFile(
                url = item.url,
                targetFile = target,
                headers = headers,
                timeoutMs = timeoutMs,
                maxRetries = options.normalizedMaxRetries
            )
        }
    }

    @Throws(IOException::class)
    private fun downloadItemToNamedTarget(
        item: DownloadUtils.NamedUrl,
        dir: Path,
        entryName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
        options: BatchDownloadOptions,
    ) {
        val target = DownloadNaming.resolveRelativeTarget(dir, entryName)
        if (options.normalizedChunkThreadsPerFile > 1) {
            rangeDownloader.downloadToFileFast(
                url = item.url,
                targetFile = target,
                headers = headers,
                timeoutMs = timeoutMs,
                chunkThreads = options.normalizedChunkThreadsPerFile,
                minSizeForChunking = DownloadDefaults.DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
                minPartSizeBytes = DownloadDefaults.DEFAULT_MIN_PART_SIZE_BYTES,
                maxRetries = options.normalizedMaxRetries
            )
        } else {
            singleFileDownloader.downloadToFile(
                url = item.url,
                targetFile = target,
                headers = headers,
                timeoutMs = timeoutMs,
                maxRetries = options.normalizedMaxRetries
            )
        }
    }

    internal enum class BatchNamingMode(
        val logTag: String,
    ) {

        FLAT_FILES("[DL-BATCH]"),
        RELATIVE_PATHS("[DL-BATCH-NAMED]")

    }

    internal data class BatchDownloadOptions(
        val maxConcurrentFiles: Int = 1,
        val chunkThreadsPerFile: Int = 1,
        val maxRetries: Int = DownloadDefaults.DEFAULT_MAX_RETRIES,
    ) {

        val normalizedMaxConcurrentFiles: Int = max(1, maxConcurrentFiles)
        val normalizedChunkThreadsPerFile: Int = max(1, chunkThreadsPerFile)
        val normalizedMaxRetries: Int = max(0, maxRetries)

    }

    private data class BatchItemResult(
        val name: String,
        val success: Boolean,
    )

}
