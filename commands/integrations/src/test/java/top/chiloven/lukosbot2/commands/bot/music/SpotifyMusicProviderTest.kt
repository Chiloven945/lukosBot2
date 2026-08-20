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
package top.chiloven.lukosbot2.commands.bot.music

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.commands.bot.music.provider.SpotifyMusicProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SpotifyMusicProviderTest {

    @Test
    fun `searchTrack fetches token and resolves track`() = runTest {
        var tokenRequested = false
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/api/token" -> {
                            tokenRequested = true
                            assertEquals(HttpMethod.Post, request.method)
                            respond(
                                content = """{"access_token":"mock_spotify_token","expires_in":3600}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        "/v1/search" -> {
                            assertEquals(
                                "Bearer mock_spotify_token",
                                request.headers[HttpHeaders.Authorization]
                            )
                            respond(
                                content = """
                                    {
                                      "tracks": {
                                        "items": [
                                          {
                                            "id": "track_123",
                                            "name": "Never Gonna Give You Up",
                                            "artists": [{"name": "Rick Astley"}],
                                            "album": {
                                              "name": "Whenever You Need Somebody",
                                              "images": [{"url": "https://example.com/cover.jpg"}]
                                            },
                                            "external_urls": {"spotify": "https://open.spotify.com/track/track_123"},
                                            "duration_ms": 213000
                                          }
                                        ]
                                      }
                                    }
                                """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }

                        else -> error("Unexpected path: ${request.url.encodedPath}")
                    }
                }
            }
        }

        val provider = SpotifyMusicProvider(client, "client_id", "client_secret")
        val track = provider.searchTrack("Never Gonna Give You Up")

        assertNotNull(track)
        assertEquals("track_123", track.id)
        assertEquals("Never Gonna Give You Up", track.name)
        assertEquals("Rick Astley", track.artist)
        assertEquals("Whenever You Need Somebody", track.album)
        assertEquals(213000L, track.durationMs)
    }

}
