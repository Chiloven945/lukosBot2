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
package top.chiloven.lukosbot2.platform.telegram

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.core.model.message.media.PlatformFileRef
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TelegramFileLoaderTest {

    @Test
    fun `load fetches file path and file payload`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/botmock_token/getFile" -> {
                            assertEquals("file_abc123", request.url.parameters["file_id"])
                            respond(
                                content = """
                                    {
                                      "ok": true,
                                      "result": {
                                        "file_id": "file_abc123",
                                        "file_path": "photos/photo_1.jpg",
                                        "file_size": 1024
                                      }
                                    }
                                """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        "/file/botmock_token/photos/photo_1.jpg" -> {
                            respond(
                                content = byteArrayOf(1, 2, 3, 4, 5),
                                status = HttpStatusCode.OK,
                                headers = headersOf(
                                    HttpHeaders.ContentType to listOf("image/jpeg")
                                )
                            )
                        }

                        else -> error("Unexpected url: ${request.url.encodedPath}")
                    }
                }
            }
        }

        val appProps = AppProperties().apply {
            telegram.botToken = "mock_token"
        }

        val loader = TelegramFileLoader(appProps, client)
        val media = loader.load(PlatformFileRef("telegram", "file_abc123"))

        assertNotNull(media)
        assertEquals(5, media.bytes().size)
        assertEquals("image/jpeg", media.mime())
        assertEquals("photo_1.jpg", media.name())
    }

}
