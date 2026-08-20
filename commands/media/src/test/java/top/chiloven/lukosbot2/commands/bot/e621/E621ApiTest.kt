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
package top.chiloven.lukosbot2.commands.bot.e621

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class E621ApiTest {

    @Test
    fun `getPosts parses posts json`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/posts.json", request.url.encodedPath)
                    assertEquals("cat", request.url.parameters["tags"])
                    respond(
                        content = """
                            {
                              "posts": [
                                {
                                  "id": 12345,
                                  "created_at": "2026-01-01T12:00:00.000Z",
                                  "updated_at": "2026-01-01T12:00:00.000Z",
                                  "file": {
                                    "width": 1000,
                                    "height": 800,
                                    "ext": "png",
                                    "size": 102400,
                                    "md5": "9f6e6800cfae7749eb6c486619254b9c",
                                    "url": "https://static1.e621.net/data/test.png"
                                  },
                                  "score": {
                                    "up": 10,
                                    "down": 0,
                                    "total": 10
                                  },
                                  "tags": {
                                    "artist": ["artist_name"]
                                  },
                                  "rating": "s"
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

        val api = E621Api(client)
        val posts = api.getPosts(tags = "cat")

        assertEquals(1, posts.size)
        val post = posts[0]
        assertEquals(12345, post.id)
        assertEquals("s", post.rating)
        assertEquals("png", post.file.ext)
        assertEquals("9f6e6800cfae7749eb6c486619254b9c", post.file.md5)
        assertEquals("artist_name", post.tags.getStringArtist())
    }

    @Test
    fun `getPostsXId parses single post json`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/posts/12345.json", request.url.encodedPath)
                    respond(
                        content = """
                            {
                              "post": {
                                "id": 12345,
                                "created_at": "2026-01-01T12:00:00.000Z",
                                "updated_at": "2026-01-01T12:00:00.000Z",
                                "file": {
                                  "width": 500,
                                  "height": 500,
                                  "ext": "jpg",
                                  "size": 50000,
                                  "md5": "abc123",
                                  "url": "https://static1.e621.net/data/test.jpg"
                                },
                                "score": {
                                  "up": 5,
                                  "down": 0,
                                  "total": 5
                                },
                                "tags": {
                                  "artist": ["demo_artist"]
                                },
                                "rating": "q"
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = E621Api(client)
        val post = api.getPostsXId("12345")

        assertNotNull(post)
        assertEquals(12345, post.id)
        assertEquals("q", post.rating)
        assertEquals("demo_artist", post.tags.getStringArtist())
    }

}
