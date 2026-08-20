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
package top.chiloven.lukosbot2.commands.bot.music.provider

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.chiloven.lukosbot2.commands.bot.music.MusicPlatform
import top.chiloven.lukosbot2.commands.bot.music.TrackInfo
import top.chiloven.lukosbot2.http.requireSuccess
import top.chiloven.lukosbot2.util.JsonUtils
import java.nio.charset.StandardCharsets
import java.util.*

class SpotifyMusicProvider(
    private val http: HttpClient,
    private val clientId: String,
    private val clientSecret: String
) : IMusicProvider {

    private companion object {

        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val API_BASE = "https://api.spotify.com/v1"
        private val TRACK_ID_RE = Regex("""/track/([^?#\s/]+)""")

    }

    private data class SpotifyTokenResponse(
        val accessToken: String? = null,
        val expiresIn: Int? = null
    )

    private data class SpotifySearchResponse(
        val tracks: SpotifyTracksDto? = null
    ) {

        data class SpotifyTracksDto(
            val items: List<SpotifyTrackDto> = emptyList()
        )

    }

    private data class SpotifyTrackDto(
        val id: String? = null,
        val name: String? = null,
        val artists: List<SpotifyArtistDto> = emptyList(),
        val album: SpotifyAlbumDto? = null,
        val externalUrls: Map<String, String>? = null,
        val durationMs: Long? = null
    ) {

        data class SpotifyArtistDto(
            val name: String? = null
        )

        data class SpotifyAlbumDto(
            val name: String? = null,
            val images: List<SpotifyImageDto> = emptyList()
        )

        data class SpotifyImageDto(
            val url: String? = null
        )

    }

    @Volatile private var accessToken: String? = null
    @Volatile private var tokenExpireAtMs: Long = 0L
    private val tokenMutex = Mutex()

    override fun platform(): MusicPlatform = MusicPlatform.SPOTIFY

    @Throws(Exception::class)
    override suspend fun searchTrack(query: String): TrackInfo? {
        val token = ensureToken()
        val response = http.get("$API_BASE/search") {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
            parameter("type", "track")
            parameter("limit", "1")
        }.requireSuccess()

        val text = response.bodyAsText()
        val dto = JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            SpotifySearchResponse::class.java
        )
        val item = dto.tracks?.items?.firstOrNull() ?: return null
        return toTrackInfo(item)
    }

    @Throws(Exception::class)
    override suspend fun resolveLink(link: String): TrackInfo? {
        val id = extractTrackIdFromLink(link)?.takeIf { it.isNotBlank() } ?: return null
        val token = ensureToken()

        val response = http.get("$API_BASE/tracks/$id") {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.Authorization, "Bearer $token")
        }.requireSuccess()

        val text = response.bodyAsText()
        val item = JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            SpotifyTrackDto::class.java
        )
        return toTrackInfo(item)
    }

    @Throws(Exception::class)
    private suspend fun ensureToken(): String {
        val now = System.currentTimeMillis()
        val cached = accessToken
        if (cached != null && now < tokenExpireAtMs - 60_000) {
            return cached
        }

        return tokenMutex.withLock {
            val currentNow = System.currentTimeMillis()
            val currentCached = accessToken
            if (currentCached != null && currentNow < tokenExpireAtMs - 60_000) {
                return@withLock currentCached
            }

            val basic = Base64.getEncoder().encodeToString(
                "$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8)
            )

            val response = http.submitForm(
                url = TOKEN_URL,
                formParameters = parameters {
                    append("grant_type", "client_credentials")
                }
            ) {
                header(HttpHeaders.Authorization, "Basic $basic")
                header(HttpHeaders.Accept, "application/json")
                timeout {
                    requestTimeoutMillis = 10_000
                }
            }.requireSuccess()

            val text = response.bodyAsText()
            val dto = JsonUtils.SNAKE_CASE_MAPPER.readValue(
                text,
                SpotifyTokenResponse::class.java
            )
            val token = dto.accessToken.orEmpty()
            val expiresIn = dto.expiresIn ?: 0

            accessToken = token
            tokenExpireAtMs = currentNow + expiresIn * 1000L
            token
        }
    }

    private fun toTrackInfo(t: SpotifyTrackDto): TrackInfo {
        val id = t.id.orEmpty()
        val name = t.name.orEmpty()
        val artist = t.artists.firstOrNull()?.name.orEmpty()
        val album = t.album?.name.orEmpty()
        val cover = t.album?.images?.firstOrNull()?.url
        val url = t.externalUrls?.get("spotify")?.takeIf {
            it.isNotBlank()
        } ?: "https://open.spotify.com/track/$id"
        val duration = t.durationMs ?: 0L
        return TrackInfo(
            platform(),
            id,
            name,
            artist,
            album,
            cover,
            url,
            duration
        )
    }

    private fun extractTrackIdFromLink(link: String?): String? {
        return if (link.isNullOrBlank()) null else {
            (TRACK_ID_RE.find(link.trim()) ?: return null).groupValues.getOrNull(1)
        }
    }

}
