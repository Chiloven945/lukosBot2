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

import okhttp3.OkHttpClient
import okhttp3.Request
import top.chiloven.lukosbot2.Constants
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Lightweight OkHttp helper for text responses that are not covered by [HttpJson] or [HttpBytes].
 *
 * Typical use cases are form POST endpoints, token endpoints, and APIs whose success body needs to
 * be inspected as plain text before the caller parses it. HTTP non-success statuses are reported as
 * [HttpStatusException] so callers can inspect structured status metadata instead of parsing an
 * exception message.
 */
object HttpText {

    private const val DEFAULT_TIMEOUT_MS: Int = 30_000
    private val UA: String = "Mozilla/5.0 (compatible; ${Constants.UA})"

    private val clientCache = OkHttpUtils.ProxyAwareOkHttpClientCache(
        connectTimeoutMs = 10_000,
        readTimeoutMs = DEFAULT_TIMEOUT_MS.toLong(),
        callTimeoutMs = DEFAULT_TIMEOUT_MS.toLong(),
    )

    private val client: OkHttpClient
        get() = clientCache.client

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun sendStringResponse(
        request: Request,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        expected: IntRange = 200..299,
    ): HttpCallResult<String> = sendStringResponse(client, request, timeoutMs, expected)

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun sendStringResponse(
        client: OkHttpClient,
        request: Request,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        expected: IntRange = 200..299,
    ): HttpCallResult<String> {
        val callClient = if (timeoutMs == DEFAULT_TIMEOUT_MS) {
            client
        } else {
            client.newBuilder()
                .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build()
        }

        val req = if (request.header("User-Agent") == null) {
            request.newBuilder().header("User-Agent", UA).build()
        } else {
            request
        }

        callClient.newCall(req).execute().use { resp ->
            val body = resp.body.string()
            if (resp.code !in expected) {
                throw HttpStatusException.fromResponse(
                    response = resp,
                    responseBodySnippet = body.take(4096),
                )
            }

            return HttpCallResult(
                body = body,
                statusCode = resp.code,
                url = resp.request.url.toString(),
                headers = resp.headers.toMultimap(),
            )
        }
    }

}
