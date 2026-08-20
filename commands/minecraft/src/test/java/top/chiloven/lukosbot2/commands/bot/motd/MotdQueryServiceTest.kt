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
package top.chiloven.lukosbot2.commands.bot.motd

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.config.ProxyConfigProp
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotdQueryServiceTest {

    @Test
    fun `queryByApi parses mcsrvstat response`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/3/play.hypixel.net", request.url.encodedPath)
                    respond(
                        content = """
                            {
                              "online": true,
                              "ip": "209.222.115.35",
                              "port": 25565,
                              "hostname": "play.hypixel.net",
                              "version": "1.8.x - 1.21.x",
                              "players": {
                                "online": 35000,
                                "max": 100000
                              },
                              "motd": {
                                "clean": ["Hypixel Network", "100+ Games"]
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val service = MotdQueryService(ProxyConfigProp(), client)
        val result = service.query("play.hypixel.net", MotdQueryService.QueryMode.API)

        assertTrue(result.online)
        assertEquals("play.hypixel.net", result.requestedAddress)
        assertEquals("209.222.115.35", result.ip)
        assertEquals(35000, result.onlinePlayers)
        assertEquals(100000, result.maxPlayers)
        assertTrue(result.formatted().contains("Hypixel Network"))
    }

}
