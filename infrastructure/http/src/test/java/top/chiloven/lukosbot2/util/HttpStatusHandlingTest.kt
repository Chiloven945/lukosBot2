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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.FormBody
import okhttp3.Request
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpStatusHandlingTest {

    @Test
    fun `HttpBytes getResponse returns status and headers`() = withServer { server ->
        server.context("/file") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.responseHeaders.add("Content-Disposition", "attachment; filename=hello.txt")
            exchange.sendText(200, "hello")
        }

        val result = HttpBytes.getResponse(server.uri("/file").toString())

        assertEquals(200, result.statusCode)
        assertEquals("hello", String(result.body.bytes, StandardCharsets.UTF_8))
        assertEquals("text/plain", result.body.mime)
        assertEquals("hello.txt", result.body.fileName)
        assertTrue(result.headers.containsKey("Content-type") || result.headers.containsKey("Content-Type"))
    }

    @Test
    fun `HttpBytes non success exposes structured status`() = withServer { server ->
        server.context("/missing") { exchange ->
            exchange.responseHeaders.add("Retry-After", "1")
            exchange.sendText(429, "slow down")
        }

        val error = assertFailsWith<HttpStatusException> {
            HttpBytes.getResponse(server.uri("/missing").toString())
        }

        assertEquals(429, error.statusCode)
        assertEquals(1_000L, error.retryAfterMs)
        assertTrue(error.retryable)
        assertTrue(error.responseBodySnippet.orEmpty().contains("slow down"))
    }

    @Test
    fun `HttpJson getObjectResponse returns status and JSON body`() = withServer { server ->
        server.context("/json") { exchange -> exchange.sendJson(200, "{\"ok\":true}") }

        val result = HttpJson.getObjectResponse(server.uri("/json"))

        assertEquals(200, result.statusCode)
        assertTrue(result.body.has("ok"))
    }

    @Test
    fun `HttpJson non JSON error still exposes structured status and snippet`() = withServer { server ->
        server.context("/boom") { exchange -> exchange.sendText(500, "temporary outage") }

        val error = assertFailsWith<HttpStatusException> {
            HttpJson.getObjectResponse(server.uri("/boom"))
        }

        assertEquals(500, error.statusCode)
        assertTrue(error.retryable)
        assertTrue(error.responseBodySnippet.orEmpty().contains("temporary outage"))
    }


    @Test
    fun `HttpText sendStringResponse posts form and returns status`() = withServer { server ->
        server.context("/form") { exchange ->
            assertEquals("POST", exchange.requestMethod)
            exchange.sendJson(200, "{\"ok\":true}")
        }

        val request = Request.Builder()
            .url(server.uri("/form").toString())
            .post(FormBody.Builder().add("q", "hello").build())
            .build()

        val result = HttpText.sendStringResponse(request)

        assertEquals(200, result.statusCode)
        assertTrue(result.body.contains("ok"))
    }

    @Test
    fun `HttpText non success exposes structured status`() = withServer { server ->
        server.context("/rate-limited") { exchange ->
            exchange.responseHeaders.add("Retry-After", "3")
            exchange.sendText(429, "slow down")
        }

        val request = Request.Builder()
            .url(server.uri("/rate-limited").toString())
            .get()
            .build()

        val error = assertFailsWith<HttpStatusException> {
            HttpText.sendStringResponse(request)
        }

        assertEquals(429, error.statusCode)
        assertEquals(3_000L, error.retryAfterMs)
        assertTrue(error.responseBodySnippet.orEmpty().contains("slow down"))
    }

    @Test
    fun `HttpStatusException parses Retry-After at exception boundary`() {
        assertEquals(2_000L, HttpStatusException.fromStatus(429, retryAfterHeader = "2").retryAfterMs)
        assertNotNull(
            HttpStatusException.fromStatus(429, retryAfterHeader = "Wed, 21 Oct 2099 07:28:00 GMT").retryAfterMs
        )
    }

    private fun withServer(block: (TestHttpServer) -> Unit) {
        TestHttpServer().use { server -> block(server) }
    }

    private class TestHttpServer : AutoCloseable {

        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val executor = Executors.newCachedThreadPool()

        init {
            server.executor = executor
            server.start()
        }

        fun uri(path: String): URI = URI.create("http://127.0.0.1:${server.address.port}$path")

        fun context(path: String, handler: (HttpExchange) -> Unit) {
            server.createContext(path) { exchange -> handler(exchange) }
        }

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}

private fun HttpExchange.sendJson(code: Int, text: String) {
    responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    sendText(code, text)
}

private fun HttpExchange.sendText(code: Int, text: String) {
    val bytes = text.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.add("Content-Length", bytes.size.toString())
    sendResponseHeaders(code, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
