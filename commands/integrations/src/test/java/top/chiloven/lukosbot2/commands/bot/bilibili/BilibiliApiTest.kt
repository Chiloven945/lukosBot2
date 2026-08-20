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
package top.chiloven.lukosbot2.commands.bot.bilibili

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.commands.bot.bilibili.schema.VideoId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BilibiliApiTest {

    @Test
    fun `getViewData parses video view response`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/x/web-interface/view", request.url.encodedPath)
                    assertEquals("BV1GJ411x7h7", request.url.parameters["bvid"])
                    respond(
                        content = """
                            {
                              "code": 0,
                              "message": "0",
                              "data": {
                                "bvid": "BV1GJ411x7h7",
                                "aid": 170001,
                                "title": "Never Gonna Give You Up",
                                "tname": "Music",
                                "desc": "Official Music Video",
                                "pic": "https://i0.hdslb.com/bfs/archive/test.jpg",
                                "pubdate": 1600000000,
                                "owner": {
                                  "mid": 123456,
                                  "name": "Rick Astley"
                                },
                                "stat": {
                                  "view": 1000000,
                                  "danmaku": 50000,
                                  "reply": 10000,
                                  "favorite": 80000,
                                  "coin": 90000,
                                  "share": 20000,
                                  "like": 120000
                                },
                                "videos": 1
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = BilibiliApi(client)
        val data = api.getViewData(VideoId.parse("BV1GJ411x7h7")!!)

        assertNotNull(data)
        assertEquals("BV1GJ411x7h7", data.bvid)
        assertEquals("Never Gonna Give You Up", data.title)
        assertEquals("Music", data.tname)
        assertEquals("Rick Astley", data.owner?.name)
        assertEquals(123456L, data.owner?.mid)
        assertEquals(1000000L, data.stat?.view)
    }

    @Test
    fun `getViewData returns null on non-zero code`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content = """{"code":-404,"message":"啥都木有"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = BilibiliApi(client)
        val data = api.getViewData(VideoId.parse("BV1GJ411x7h7")!!)
        assertNull(data)
    }

    @Test
    fun `getFollowerCount parses relation stat response`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/x/relation/stat", request.url.encodedPath)
                    assertEquals("123456", request.url.parameters["vmid"])
                    respond(
                        content = """
                            {
                              "code": 0,
                              "data": {
                                "mid": 123456,
                                "follower": 9999
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = BilibiliApi(client)
        val followers = api.getFollowerCount(123456L)
        assertEquals(9999L, followers)
    }

}
