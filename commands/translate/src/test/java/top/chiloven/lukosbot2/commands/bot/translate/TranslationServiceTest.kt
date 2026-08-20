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
package top.chiloven.lukosbot2.commands.bot.translate

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.config.CommandConfigProp
import kotlin.test.assertEquals

class TranslationServiceTest {

    @Test
    fun `translate submits form and returns translated text`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/translate", request.url.encodedPath)
                    assertEquals(HttpMethod.Post, request.method)
                    respond(
                        content = """{"translatedText":"Bonjour le monde"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val config = CommandConfigProp.Translate().apply {
            url = "http://mock-libretranslate:5000"
            defaultLang = "fr"
        }

        val service = TranslationService(config, client)
        val result = service.translate("en", "fr", "Hello world")

        assertEquals("Bonjour le monde", result)
    }

    @Test
    fun `translate handles non 2xx with formatted error message`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content = """{"error":"Invalid target language"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val config = CommandConfigProp.Translate().apply {
            url = "http://mock-libretranslate:5000"
            defaultLang = "fr"
        }

        val service = TranslationService(config, client)
        val result = service.translate("en", "xyz", "Hello world")

        assertEquals("翻译失败（HTTP 400）：{\"error\":\"Invalid target language\"}", result)
    }

}
