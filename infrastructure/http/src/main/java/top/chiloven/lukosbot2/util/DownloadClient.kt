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

import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.util.download.*
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Service for resilient HTTP downloads.
 */
class DownloadClient(
    private val proxyConfig: ProxyConfigProp? = null,
) {

    private val http = DownloadHttp(proxyProvider = { proxyConfig })
    private val retryPolicyFactory: (Int) -> RetryPolicy = { RetryPolicy.default(it) }
    private val singleFileDownloader = SingleFileDownloader(http, retryPolicyFactory)
    private val rangeDownloader = RangeDownloader(http, singleFileDownloader, retryPolicyFactory)
    private val batchDownloader = BatchDownloader(singleFileDownloader, rangeDownloader)

    @Throws(IOException::class)
    fun downloadAllToDir(
        items: List<DownloadUtils.NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
    ): DownloadUtils.BatchResult = batchDownloader.download(
        items = items,
        dir = dir,
        headers = headers,
        timeoutMs = timeoutMs,
        namingMode = BatchDownloader.BatchNamingMode.FLAT_FILES,
        options = BatchDownloader.BatchDownloadOptions()
    )

    @JvmOverloads
    @Throws(IOException::class)
    fun downloadAllToDirConcurrent(
        items: List<DownloadUtils.NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxConcurrentFiles: Int = DownloadDefaults.DEFAULT_MAX_CONCURRENT_FILES,
        chunkThreadsPerFile: Int = 1,
        maxRetries: Int = DownloadDefaults.DEFAULT_MAX_RETRIES,
    ): DownloadUtils.BatchResult = batchDownloader.download(
        items = items,
        dir = dir,
        headers = headers,
        timeoutMs = timeoutMs,
        namingMode = BatchDownloader.BatchNamingMode.FLAT_FILES,
        options = BatchDownloader.BatchDownloadOptions(
            maxConcurrentFiles,
            chunkThreadsPerFile,
            maxRetries
        )
    )

    @JvmOverloads
    @Throws(IOException::class)
    fun downloadNamedUrlsToDirConcurrent(
        items: List<DownloadUtils.NamedUrl?>?,
        dir: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxConcurrentFiles: Int = DownloadDefaults.DEFAULT_MAX_CONCURRENT_FILES,
        chunkThreadsPerFile: Int = DownloadDefaults.DEFAULT_CHUNK_THREADS,
        maxRetries: Int = DownloadDefaults.DEFAULT_MAX_RETRIES,
    ): DownloadUtils.BatchResult = batchDownloader.download(
        items = items,
        dir = dir,
        headers = headers,
        timeoutMs = timeoutMs,
        namingMode = BatchDownloader.BatchNamingMode.RELATIVE_PATHS,
        options = BatchDownloader.BatchDownloadOptions(
            maxConcurrentFiles,
            chunkThreadsPerFile,
            maxRetries
        )
    )

    @JvmOverloads
    @Throws(IOException::class)
    fun downloadToFile(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxRetries: Int = DownloadDefaults.DEFAULT_MAX_RETRIES,
    ) {
        singleFileDownloader.downloadToFile(url, targetFile, headers, timeoutMs, maxRetries)
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun downloadToDir(
        url: URI,
        dir: Path,
        fileName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
        maxRetries: Int = DownloadDefaults.DEFAULT_MAX_RETRIES,
    ): Path {
        Files.createDirectories(dir)
        val target = DownloadNaming.resolveFlatTarget(dir, fileName)
        downloadToFile(url, target, headers, timeoutMs, maxRetries)
        return target
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun downloadToFileFast(
        url: URI,
        targetFile: Path,
        headers: Map<String, String>?,
        timeoutMs: Int,
        chunkThreads: Int = DownloadDefaults.DEFAULT_CHUNK_THREADS,
        minSizeForChunking: Long = DownloadDefaults.DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
        minPartSizeBytes: Long = DownloadDefaults.DEFAULT_MIN_PART_SIZE_BYTES,
        maxRetries: Int = DownloadDefaults.DEFAULT_MAX_RETRIES,
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

    @JvmOverloads
    @Throws(IOException::class)
    fun downloadToDirFast(
        url: URI,
        dir: Path,
        fileName: String,
        headers: Map<String, String>?,
        timeoutMs: Int,
        chunkThreads: Int = DownloadDefaults.DEFAULT_CHUNK_THREADS,
        maxRetries: Int = DownloadDefaults.DEFAULT_MAX_RETRIES,
    ): Path {
        Files.createDirectories(dir)
        val target = DownloadNaming.resolveFlatTarget(dir, fileName)
        downloadToFileFast(
            url = url,
            targetFile = target,
            headers = headers,
            timeoutMs = timeoutMs,
            chunkThreads = chunkThreads,
            minSizeForChunking = DownloadDefaults.DEFAULT_MIN_SIZE_FOR_CHUNKING_BYTES,
            minPartSizeBytes = DownloadDefaults.DEFAULT_MIN_PART_SIZE_BYTES,
            maxRetries = maxRetries
        )
        return target
    }

}
