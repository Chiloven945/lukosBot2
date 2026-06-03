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

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.logging.log4j.LogManager
import top.chiloven.lukosbot2.Constants
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class DownloadHttp {

    private val log = LogManager.getLogger(DownloadHttp::class.java)
    private val clientCache = OkHttpUtils.ProxyAwareOkHttpClientCache(connectTimeoutMs = 20_000)

    @Volatile
    private var timeoutClientCache: TimeoutClientCache? = null

    @Throws(IOException::class)
    fun execute(request: Request, timeoutMs: Int): Response =
        clientFor(timeoutMs)
            .newCall(request)
            .execute()

    fun buildGet(
        url: URI,
        headers: Map<String, String>?,
    ): Request = buildRequest(
        url = url,
        headers = headers,
        forceIdentityEncoding = false,
        rangeStart = null,
        ifRangeToken = null,
        rangeEnd = null,
        method = "GET"
    )

    fun buildHead(
        url: URI,
        headers: Map<String, String>?,
    ): Request = buildRequest(
        url = url,
        headers = headers,
        forceIdentityEncoding = false,
        rangeStart = null,
        ifRangeToken = null,
        rangeEnd = null,
        method = "HEAD"
    )

    fun buildRangeGet(
        url: URI,
        headers: Map<String, String>?,
        rangeStart: Long,
        rangeEnd: Long? = null,
        ifRangeToken: String? = null,
    ): Request = buildRequest(
        url = url,
        headers = headers,
        forceIdentityEncoding = true,
        rangeStart = rangeStart,
        ifRangeToken = ifRangeToken,
        rangeEnd = rangeEnd,
        method = "GET"
    )

    fun debugResponseSummary(
        url: URI,
        code: Int,
        headers: Headers,
        askedRange: Boolean,
        rangeStart: Long,
    ) {
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

    fun guessTotalSize(code: Int, headers: Headers): Long =
        if (code == 206) headers["Content-Range"]?.let(::parseTotalFromContentRange) ?: -1L
        else headers["Content-Length"]?.toLongOrNull() ?: -1L

    fun pickIfRangeToken(headers: Headers): String? =
        headers["ETag"]?.takeUnless(String::isBlank)
            ?: headers["Last-Modified"]?.takeUnless(String::isBlank)

    fun parseTotalFromContentRange(value: String?): Long {
        if (value.isNullOrBlank()) return -1L
        val slash = value.lastIndexOf('/')
        if (slash < 0 || slash + 1 >= value.length) return -1L
        val total = value.substring(slash + 1).trim()
        if (total.isEmpty() || total == "*") return -1L
        return total.toLongOrNull() ?: -1L
    }

    private fun clientFor(timeoutMs: Int): OkHttpClient {
        val baseClient = clientCache.client
        val normalizedTimeout = max(1, timeoutMs)
        val current = timeoutClientCache
        if (current != null && current.baseClient === baseClient) {
            current.clients[normalizedTimeout]?.let { return it }
        }

        synchronized(this) {
            val latest = timeoutClientCache
            val cache = if (latest != null && latest.baseClient === baseClient) {
                latest
            } else {
                TimeoutClientCache(baseClient)
            }

            cache.clients[normalizedTimeout]?.let {
                timeoutClientCache = cache
                return it
            }

            val client = baseClient.newBuilder()
                .callTimeout(normalizedTimeout.toLong(), TimeUnit.MILLISECONDS)
                .build()
            cache.clients[normalizedTimeout] = client
            timeoutClientCache = cache
            return client
        }
    }

    private fun buildRequest(
        url: URI,
        headers: Map<String, String>?,
        forceIdentityEncoding: Boolean,
        rangeStart: Long?,
        ifRangeToken: String?,
        rangeEnd: Long?,
        method: String,
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

    private class TimeoutClientCache(
        val baseClient: OkHttpClient,
    ) {

        val clients: MutableMap<Int, OkHttpClient> = mutableMapOf()

    }

}
