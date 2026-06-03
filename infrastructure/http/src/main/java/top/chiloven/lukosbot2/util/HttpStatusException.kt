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

import okhttp3.Response
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * Structured exception for HTTP responses whose status code is outside the expected success range.
 *
 * Transport-level failures such as DNS, TLS, socket timeout or interrupted reads should continue to
 * surface as plain [IOException] or a subtype. This exception means an HTTP response was received and
 * its status code can be inspected safely by callers.
 */
open class HttpStatusException @JvmOverloads constructor(
    val statusCode: Int,
    val method: String? = null,
    val url: String? = null,
    val responseBodySnippet: String? = null,
    val retryAfterMs: Long? = null,
    val responseHeaders: Map<String, List<String>> = emptyMap(),
    message: String = buildMessage(statusCode, method, url, responseBodySnippet),
    cause: Throwable? = null,
) : IOException(message, cause) {

    val retryable: Boolean
        get() = isRetryableStatus(statusCode)

    companion object {

        @JvmStatic
        @JvmOverloads
        fun fromResponse(
            response: Response,
            responseBodySnippet: String? = runCatching { response.peekBody(4096).string() }.getOrNull(),
            message: String? = null,
            cause: Throwable? = null,
        ): HttpStatusException {
            val method = response.request.method
            val url = response.request.url.toString()
            return HttpStatusException(
                statusCode = response.code,
                method = method,
                url = url,
                responseBodySnippet = responseBodySnippet,
                retryAfterMs = parseRetryAfterMs(response.header("Retry-After")),
                responseHeaders = response.headers.toMultimap(),
                message = message ?: buildMessage(response.code, method, url, responseBodySnippet),
                cause = cause,
            )
        }

        @JvmStatic
        @JvmOverloads
        fun fromStatus(
            statusCode: Int,
            method: String? = null,
            url: String? = null,
            responseBodySnippet: String? = null,
            retryAfterHeader: String? = null,
            responseHeaders: Map<String, List<String>> = emptyMap(),
            message: String? = null,
            cause: Throwable? = null,
        ): HttpStatusException = HttpStatusException(
            statusCode = statusCode,
            method = method,
            url = url,
            responseBodySnippet = responseBodySnippet,
            retryAfterMs = parseRetryAfterMs(retryAfterHeader),
            responseHeaders = responseHeaders,
            message = message ?: buildMessage(statusCode, method, url, responseBodySnippet),
            cause = cause,
        )

        @JvmStatic
        fun isRetryableStatus(code: Int): Boolean =
            code == 408 || code == 429 || code in 500..599

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

        private fun buildMessage(
            statusCode: Int,
            method: String?,
            url: String?,
            snippet: String?,
        ): String = buildString {
            append("HTTP ").append(statusCode)
            if (!method.isNullOrBlank()) append(' ').append(method)
            if (!url.isNullOrBlank()) append(' ').append(url)
            if (!snippet.isNullOrBlank()) {
                append(": ").append(snippet.lineSequence().firstOrNull().orEmpty())
            }
        }

        @JvmStatic
        @JvmOverloads
        fun message(e: Throwable, defaultAction: String = "请求失败"): String = when (e) {
            is HttpStatusException -> when (e.statusCode) {
                400 -> "$defaultAction：请求参数无效。"
                401, 403 -> "$defaultAction：远端拒绝访问或凭据无效。"
                404 -> "$defaultAction：资源不存在。"
                408 -> "$defaultAction：远端响应超时。"
                429 -> "$defaultAction：请求过于频繁，请稍后再试。"
                in 500..599 -> "$defaultAction：远端服务暂时不可用。"
                else -> "$defaultAction：HTTP ${e.statusCode}。"
            }

            else -> e.message?.takeIf { it.isNotBlank() } ?: "$defaultAction，请稍后再试。"
        }

        fun HttpStatusException.friendly(defaultAction: String = "请求失败"): String =
            message(this, defaultAction)

    }

}
