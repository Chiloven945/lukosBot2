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
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Public facade for resilient HTTP downloads.
 */
object DownloadClient {

    const val DEFAULT_MAX_CONCURRENT_FILES: Int = DownloadDefaults.DEFAULT_MAX_CONCURRENT_FILES
    const val DEFAULT_CHUNK_THREADS: Int = DownloadDefaults.DEFAULT_CHUNK_THREADS
    const val DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES: Long = DownloadDefaults.DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES
    const val DEFAULT_MIN_PART_SIZE_BYTES: Long = DownloadDefaults.DEFAULT_MIN_PART_SIZE_BYTES
    const val DEFAULT_MAX_RETRIES: Int = DownloadDefaults.DEFAULT_MAX_RETRIES
    const val DEFAULT_RETRY_BASE_DELAY_MS: Long = DownloadDefaults.DEFAULT_RETRY_BASE_DELAY_MS
    const val DEFAULT_RETRY_MAX_DELAY_MS: Long = DownloadDefaults.DEFAULT_RETRY_MAX_DELAY_MS
    const val DEFAULT_RETRY_AFTER_CAP_MS: Long = DownloadDefaults.DEFAULT_RETRY_AFTER_CAP_MS
    const val DEFAULT_PROGRESS_LOG_INTERVAL_MS: Long = DownloadDefaults.DEFAULT_PROGRESS_LOG_INTERVAL_MS

    private val http = DownloadHttp()
    private val retryPolicyFactory: (Int) -> RetryPolicy = { RetryPolicy.default(it) }
    private val singleFileDownloader = SingleFileDownloader(http, retryPolicyFactory)
    private val rangeDownloader = RangeDownloader(http, singleFileDownloader, retryPolicyFactory)
    private val batchDownloader = BatchDownloader(singleFileDownloader, rangeDownloader)

    @JvmStatic
    @Throws(IOException::class)
    fun downloadAllToDir(
        items: List<NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
    ): BatchResult = batchDownloader.download(
        items = items,
        dir = dir,
        headers = headers,
        timeoutMs = timeoutMs,
        namingMode = BatchDownloader.BatchNamingMode.FLAT_FILES,
        options = BatchDownloader.BatchDownloadOptions()
    )

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
    ): BatchResult = batchDownloader.download(
        items = items,
        dir = dir,
        headers = headers,
        timeoutMs = timeoutMs,
        namingMode = BatchDownloader.BatchNamingMode.FLAT_FILES,
        options = BatchDownloader.BatchDownloadOptions(maxConcurrentFiles, chunkThreadsPerFile, maxRetries)
    )

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
    ): BatchResult = batchDownloader.download(
        items = items,
        dir = dir,
        headers = headers,
        timeoutMs = timeoutMs,
        namingMode = BatchDownloader.BatchNamingMode.RELATIVE_PATHS,
        options = BatchDownloader.BatchDownloadOptions(maxConcurrentFiles, chunkThreadsPerFile, maxRetries)
    )

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
        singleFileDownloader.downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
    }

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
        val target = DownloadNaming.resolveFlatTarget(dir, fileName)
        downloadToFile(url, target, headers, timeoutMs, maxRetries)
        return target
    }

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
        rangeDownloader.downloadToFileFast(
            url = url,
            targetFile = targetFile,
            headers = headers,
            timeoutMs = timeoutMs,
            chunkThreads = chunkThreads,
            minSizeForChunking = minSizeForChunking,
            minPartSizeBytes = minPartSizeBytes,
            maxRetries = maxRetries
        )
    }

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
        val target = DownloadNaming.resolveFlatTarget(dir, fileName)
        downloadToFileFast(
            url = url,
            targetFile = target,
            headers = headers,
            timeoutMs = timeoutMs,
            chunkThreads = chunkThreads,
            minSizeForChunking = DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
            minPartSizeBytes = DEFAULT_MIN_PART_SIZE_BYTES,
            maxRetries = maxRetries
        )
        return target
    }

    @JvmStatic
    fun sanitizeFileName(name: String?): String = DownloadNaming.flatFileName(name)

    data class NamedUrl(
        val name: String,
        val url: URI,
    ) {

        fun name(): String = name
        fun url(): URI = url

    }

    data class BatchResult(
        val ok: Int,
        val failed: List<String>,
    ) {

        fun ok(): Int = ok
        fun failed(): List<String> = failed

    }

}
