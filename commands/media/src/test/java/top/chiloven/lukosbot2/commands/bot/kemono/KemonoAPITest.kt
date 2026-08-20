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
package top.chiloven.lukosbot2.commands.bot.kemono

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.commands.bot.kemono.schema.Service
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KemonoAPITest {

    @Test
    fun `getSpecificPost decodes post object`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/api/v1/patreon/user/123/post/456", request.url.encodedPath)
                    assertEquals("text/css", request.headers[HttpHeaders.Accept])
                    respond(
                        content = """
                            {
                              "post": {
                                "id": "456",
                                "user": "123",
                                "service": "patreon",
                                "title": "Sample Post",
                                "content": "Post content here",
                                "added": "2026-01-01T12:00:00.000",
                                "published": "2026-01-01T12:00:00.000",
                                "edited": "2026-01-01T12:00:00.000",
                                "attachments": [
                                  {
                                    "name": "image.png",
                                    "path": "/data/image.png"
                                  }
                                ]
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = KemonoAPI(client)
        val post = api.getSpecificPost(Service.PATREON, "123", "456")

        assertNotNull(post)
        assertEquals("456", post.id)
        assertEquals("123", post.user)
        assertEquals(Service.PATREON, post.service)
        assertEquals("Sample Post", post.title)
        assertEquals(1, post.attachments.size)
        assertEquals("image.png", post.attachments[0].name)
    }

    @Test
    fun `getFileFromHash decodes hash search file`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/api/v1/search_hash/15be29bad5f6", request.url.encodedPath)
                    respond(
                        content = """
                            {
                              "id": "file_1",
                              "hash": "15be29bad5f6",
                              "mtime": "2026-01-01T12:00:00.000",
                              "ctime": "2026-01-01T12:00:00.000",
                              "added": "2026-01-01T12:00:00.000",
                              "mime": "image/png",
                              "ext": ".png",
                              "size": 1024,
                              "posts": [
                                {
                                  "id": "456",
                                  "user": "123",
                                  "service": "patreon",
                                  "title": "Sample Post",
                                  "published": "2026-01-01T12:00:00.000"
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = KemonoAPI(client)
        val file = api.getFileFromHash("15be29bad5f6")

        assertNotNull(file)
        assertEquals("15be29bad5f6", file.hash)
        assertEquals(".png", file.ext)
        assertEquals(1, file.posts.size)
        assertEquals("456", file.posts[0].id)
    }

}
