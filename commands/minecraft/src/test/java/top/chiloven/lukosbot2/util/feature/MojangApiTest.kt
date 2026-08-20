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
package top.chiloven.lukosbot2.util.feature

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MojangApiTest {

    @Test
    fun `getUuidFromName returns uuid on success`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/users/profiles/minecraft/jeb_", request.url.encodedPath)
                    respond(
                        content = """{"id":"853c80ef3c3749fdaa49938b674adae6","name":"jeb_"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = MojangApi(client)
        val uuid = api.getUuidFromName("jeb_")
        assertEquals("853c80ef3c3749fdaa49938b674adae6", uuid)
    }

    @Test
    fun `getNameFromUuid returns player name on success`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(
                        "/minecraft/profile/lookup/853c80ef3c3749fdaa49938b674adae6",
                        request.url.encodedPath
                    )
                    respond(
                        content = """{"id":"853c80ef3c3749fdaa49938b674adae6","name":"jeb_"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = MojangApi(client)
        val name = api.getNameFromUuid("853c80ef3c3749fdaa49938b674adae6")
        assertEquals("jeb_", name)
    }

    @Test
    fun `getMcPlayerInfo decodes profile and textures`() = runTest {
        val rawTexturesJson = """{"textures":{"SKIN":{"url":"https://textures.minecraft.net/texture/skin123"}}}"""
        val base64Textures = Base64.getEncoder().encodeToString(rawTexturesJson.toByteArray())

        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/users/profiles/minecraft/jeb_" -> respond(
                            content = """{"id":"853c80ef3c3749fdaa49938b674adae6","name":"jeb_"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )

                        "/session/minecraft/profile/853c80ef3c3749fdaa49938b674adae6" -> respond(
                            content = """
                                {
                                  "id": "853c80ef3c3749fdaa49938b674adae6",
                                  "name": "jeb_",
                                  "properties": [
                                    {
                                      "name": "textures",
                                      "value": "$base64Textures"
                                    }
                                  ]
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )

                        else -> error("Unexpected url: ${request.url.encodedPath}")
                    }
                }
            }
        }

        val api = MojangApi(client)
        val player = api.getMcPlayerInfo("jeb_")
        assertNotNull(player)
        assertEquals("jeb_", player.name)
        assertEquals("853c80ef3c3749fdaa49938b674adae6", player.uuid)
        assertEquals("https://textures.minecraft.net/texture/skin123", player.skin)
    }

}
