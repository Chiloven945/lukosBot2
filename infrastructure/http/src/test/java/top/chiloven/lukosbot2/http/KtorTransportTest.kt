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

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.util.HttpStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KtorTransportTest {

    @Test
    fun `requireSuccess returns response on 2xx`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content = """{"status":"ok"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val response = client.get("https://example.com/api").requireSuccess()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `requireSuccess throws HttpStatusException on non-2xx`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content = "Too Many Requests",
                        status = HttpStatusCode.TooManyRequests,
                        headers = headersOf(HttpHeaders.RetryAfter, "2")
                    )
                }
            }
        }

        val error = assertFailsWith<HttpStatusException> {
            client.get("https://example.com/api").requireSuccess()
        }

        assertEquals(429, error.statusCode)
        assertEquals("GET", error.method)
        assertEquals("https://example.com/api", error.url)
        assertEquals(2_000L, error.retryAfterMs)
        assertTrue(error.retryable)
        assertEquals(true, error.responseBodySnippet?.contains("Too Many Requests"))
    }

    @Test
    fun `readBytePayload parses bytes mime and content-disposition filename`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content = byteArrayOf(1, 2, 3, 4),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf("image/png; charset=utf-8"),
                            HttpHeaders.ContentDisposition to listOf("""attachment; filename="avatar.png"""")
                        )
                    )
                }
            }
        }

        val payload = client.get("https://example.com/download/file").readBytePayload()
        assertEquals(4, payload.bytes.size)
        assertEquals("image/png", payload.mime)
        assertEquals("avatar.png", payload.fileName)
    }

    @Test
    fun `readBytePayload falls back to URL filename when content-disposition absent`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content = byteArrayOf(10, 20),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")
                    )
                }
            }
        }

        val payload = client.get("https://example.com/images/cat.jpg").readBytePayload()
        assertEquals(2, payload.bytes.size)
        assertEquals("application/octet-stream", payload.mime)
        assertEquals("cat.jpg", payload.fileName)
    }

    @Test
    fun `createHttpClient configures defaults`() {
        val client = HttpClientConfiguration.createHttpClient()
        assertNotNull(client)
        client.close()
    }

}
