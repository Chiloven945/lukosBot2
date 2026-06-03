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
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DownloadClientTest {

    @Test
    fun `downloadToFile writes body and removes part file`() = withServer { server ->
        server.respondBytes("/file", "hello".toByteArray(StandardCharsets.UTF_8))

        val dir = Files.createTempDirectory("download-client-test-")
        try {
            val target = dir.resolve("hello.txt")
            DownloadUtils.downloadToFile(server.uri("/file"), target, null, 10_000, maxRetries = 0)

            assertEquals("hello", target.readText())
            assertFalse(Files.exists(PathUtils.tempSiblingPath(target)))
        } finally {
            PathUtils.deleteRecursively(dir)
        }
    }

    @Test
    fun `downloadToFile retries retryable HTTP status`() = withServer { server ->
        val calls = AtomicInteger()
        server.context("/flaky") { exchange ->
            if (calls.incrementAndGet() == 1) {
                exchange.sendText(500, "nope")
            } else {
                exchange.sendText(200, "ok")
            }
        }

        val dir = Files.createTempDirectory("download-client-test-")
        try {
            val target = dir.resolve("flaky.txt")
            DownloadUtils.downloadToFile(server.uri("/flaky"), target, null, 10_000, maxRetries = 1)

            assertEquals("ok", target.readText())
            assertEquals(2, calls.get())
        } finally {
            PathUtils.deleteRecursively(dir)
        }
    }

    @Test
    fun `HTTP status exception supports seconds and RFC date Retry-After`() {
        val seconds = HttpStatusException.fromStatus(429, retryAfterHeader = "1").retryAfterMs
        val date = ZonedDateTime.now().plusSeconds(2).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val dateDelay = HttpStatusException.fromStatus(429, retryAfterHeader = date).retryAfterMs

        assertEquals(1_000L, seconds)
        assertNotNull(dateDelay)
        assertTrue(dateDelay > 0)
    }

    @Test
    fun `downloadToFileFast uses ranged parts when server supports ranges`() = withServer { server ->
        val data = "0123456789abcdef".toByteArray(StandardCharsets.UTF_8)
        val rangeGets = AtomicInteger()
        server.context("/range") { exchange ->
            when (exchange.requestMethod.uppercase()) {
                "HEAD" -> {
                    exchange.responseHeaders.add("Accept-Ranges", "bytes")
                    exchange.responseHeaders.add("Content-Length", data.size.toString())
                    exchange.sendResponseHeaders(200, -1)
                    exchange.close()
                }

                else -> {
                    val range = exchange.requestHeaders.getFirst("Range")
                    if (range != null) {
                        rangeGets.incrementAndGet()
                        exchange.sendRange(data, range)
                    } else {
                        exchange.sendBytes(200, data)
                    }
                }
            }
        }

        val dir = Files.createTempDirectory("download-client-test-")
        try {
            val target = dir.resolve("range.bin")
            DownloadUtils.downloadToFileFast(
                url = server.uri("/range"),
                targetFile = target,
                headers = null,
                timeoutMs = 10_000,
                chunkThreads = 3,
                minSizeForChunking = 1,
                minPartSizeBytes = 1,
                maxRetries = 0
            )

            assertEquals("0123456789abcdef", target.readText())
            assertTrue(rangeGets.get() >= 2)
        } finally {
            PathUtils.deleteRecursively(dir)
        }
    }

    @Test
    fun `named batch preserves relative paths and deduplicates duplicate entries`() = withServer { server ->
        server.respondBytes("/ok", "x".toByteArray(StandardCharsets.UTF_8))

        val dir = Files.createTempDirectory("download-client-test-")
        try {
            val result = DownloadUtils.downloadNamedUrlsToDirConcurrent(
                items = listOf(
                    DownloadUtils.NamedUrl("a/b/file.txt", server.uri("/ok")),
                    DownloadUtils.NamedUrl("a/b/file.txt", server.uri("/ok"))
                ),
                dir = dir,
                headers = null,
                timeoutMs = 10_000,
                maxConcurrentFiles = 2,
                chunkThreadsPerFile = 1,
                maxRetries = 0
            )

            assertEquals(2, result.ok())
            assertTrue(result.failed().isEmpty())
            assertTrue(Files.exists(dir.resolve("a/b/file.txt")))
            assertTrue(Files.exists(dir.resolve("a/b/file (2).txt")))
        } finally {
            PathUtils.deleteRecursively(dir)
        }
    }

    @Test
    fun `named batch rejects path traversal`() = withServer { server ->
        server.respondBytes("/ok", "x".toByteArray(StandardCharsets.UTF_8))

        val dir = Files.createTempDirectory("download-client-test-")
        try {
            val result = DownloadUtils.downloadNamedUrlsToDirConcurrent(
                items = listOf(DownloadUtils.NamedUrl("../evil.txt", server.uri("/ok"))),
                dir = dir,
                headers = null,
                timeoutMs = 10_000,
                maxConcurrentFiles = 1,
                chunkThreadsPerFile = 1,
                maxRetries = 0
            )

            assertEquals(0, result.ok())
            assertEquals(listOf("../evil.txt"), result.failed())
            assertFalse(Files.exists(dir.parent.resolve("evil.txt")))
        } finally {
            PathUtils.deleteRecursively(dir)
        }
    }

    private fun withServer(block: (TestHttpServer) -> Unit) {
        TestHttpServer().use { server ->
            block(server)
        }
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

        fun respondBytes(path: String, bytes: ByteArray) {
            context(path) { exchange -> exchange.sendBytes(200, bytes) }
        }

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }

    }

}

private fun HttpExchange.sendText(code: Int, text: String) {
    sendBytes(code, text.toByteArray(StandardCharsets.UTF_8))
}

private fun HttpExchange.sendBytes(code: Int, bytes: ByteArray) {
    responseHeaders.add("Content-Length", bytes.size.toString())
    if (requestMethod.equals("HEAD", ignoreCase = true)) {
        sendResponseHeaders(code, -1)
        close()
        return
    }

    sendResponseHeaders(code, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private fun HttpExchange.sendRange(data: ByteArray, range: String) {
    val normalized = range.removePrefix("bytes=")
    val start = normalized.substringBefore('-').toInt()
    val end = normalized.substringAfter('-', data.lastIndex.toString()).toInt()
    val actualEnd = end.coerceAtMost(data.lastIndex)
    val bytes = data.copyOfRange(start, actualEnd + 1)

    responseHeaders.add("Accept-Ranges", "bytes")
    responseHeaders.add("Content-Range", "bytes $start-$actualEnd/${data.size}")
    responseHeaders.add("Content-Length", bytes.size.toString())
    sendResponseHeaders(206, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
