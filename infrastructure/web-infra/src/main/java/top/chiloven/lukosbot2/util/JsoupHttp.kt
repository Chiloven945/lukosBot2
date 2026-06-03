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

import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.Proxy

/**
 * Jsoup GET helper that turns non-2xx HTTP responses into [HttpStatusException].
 */
object JsoupHttp {

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun getDocument(
        url: String,
        userAgent: String,
        timeoutMs: Int,
        proxy: Proxy? = null,
        configure: (Connection) -> Unit = {},
    ): Document {
        val conn = Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(timeoutMs)
            .ignoreHttpErrors(true)

        if (proxy != null && proxy != Proxy.NO_PROXY) {
            conn.proxy(proxy)
        }
        configure(conn)

        val resp = conn.execute()
        if (resp.statusCode() !in 200..299) {
            throw HttpStatusException(
                statusCode = resp.statusCode(),
                method = "GET",
                url = url,
                responseBodySnippet = resp.body().take(4096),
                responseHeaders = resp.headers().mapValues { listOf(it.value) },
            )
        }

        return resp.parse()
    }
}
