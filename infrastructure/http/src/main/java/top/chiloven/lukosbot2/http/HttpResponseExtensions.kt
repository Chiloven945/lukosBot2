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
package top.chiloven.lukosbot2.http

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import top.chiloven.lukosbot2.util.HttpStatusException
import top.chiloven.lukosbot2.util.PathUtils
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class BytePayload(
    val bytes: ByteArray,
    val mime: String?,
    val fileName: String?
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BytePayload
        if (!bytes.contentEquals(other.bytes)) return false
        if (mime != other.mime) return false
        if (fileName != other.fileName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mime.hashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }

}

@Throws(HttpStatusException::class)
suspend fun HttpResponse.requireSuccess(): HttpResponse {
    if (status.isSuccess()) return this
    throw toHttpStatusException()
}

suspend fun HttpResponse.toHttpStatusException(snippetLimit: Int = 4096): HttpStatusException {
    val snippet = runCatching { bodyAsText().take(snippetLimit) }.getOrNull()
    val method = request.method.value
    val url = request.url.toString()
    val retryAfter = headers[HttpHeaders.RetryAfter]
    val responseHeaders = headers.entries().associate { it.key to it.value }
    return HttpStatusException.fromStatus(
        statusCode = status.value,
        method = method,
        url = url,
        responseBodySnippet = snippet,
        retryAfterHeader = retryAfter,
        responseHeaders = responseHeaders,
    )
}

suspend fun HttpResponse.readBytePayload(): BytePayload {
    requireSuccess()
    val bytes: ByteArray = body()
    val contentType = headers[HttpHeaders.ContentType]
    val mime = contentType?.substringBefore(';')?.trim()?.ifBlank { null }
    val disposition = headers[HttpHeaders.ContentDisposition]
    val fileName = parseFileName(disposition)
        ?: PathUtils.inferFileNameFromUrl(request.url.toString())
    return BytePayload(bytes = bytes, mime = mime, fileName = fileName)
}

private fun parseFileName(contentDisposition: String?): String? {
    if (contentDisposition.isNullOrBlank()) return null
    val parts = contentDisposition.split(';')
    for (part in parts) {
        val trimmed = part.trim()
        when {
            trimmed.startsWith("filename*=", ignoreCase = true) -> {
                val raw = trimmed.substringAfter('=', "").trim().trim('"')
                val encoded = raw.substringAfter("''", raw)
                return runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8) }
                        .getOrNull()
                        ?.ifBlank { null }
            }

            trimmed.startsWith("filename=", ignoreCase = true) -> {
                return trimmed.substringAfter('=', "").trim().trim('"').ifBlank { null }
            }
        }
    }
    return null
}
