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

import okhttp3.FormBody
import okhttp3.Request
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.commands.bot.music.MusicPlatform
import top.chiloven.lukosbot2.commands.bot.music.TrackInfo
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.HttpText
import top.chiloven.lukosbot2.util.JsonUtils.MAPPER
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.long
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*

class SpotifyMusicProvider(
    private val clientId: String,
    private val clientSecret: String
) : IMusicProvider {

    private companion object {

        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val API_BASE = "https://api.spotify.com/v1"
        private val TRACK_ID_RE = Regex("""/track/([^?#\s/]+)""")

    }

    @Volatile
    private var accessToken: String? = null
    @Volatile
    private var tokenExpireAtMs: Long = 0L

    override fun platform(): MusicPlatform = MusicPlatform.SPOTIFY

    @Throws(Exception::class)
    override fun searchTrack(query: String): TrackInfo? {
        val token = ensureToken()
        val root = HttpJson.getObjectResponse(
            uri = URI("$API_BASE/search"),
            params = mapOf(
                "q" to query,
                "type" to "track",
                "limit" to "1",
            ),
            headers = bearerHeaders(token),
        ).body

        val tracks = root.obj("tracks") ?: return null
        val items = tracks.arr("items") ?: return null
        if (items.size() == 0) return null

        return toTrackInfo(items[0].asObject())
    }

    @Throws(Exception::class)
    override fun resolveLink(link: String): TrackInfo? {
        val id = extractTrackIdFromLink(link)?.takeIf { it.isNotBlank() } ?: return null
        val token = ensureToken()

        val root = HttpJson.getObjectResponse(
            uri = URI("$API_BASE/tracks/$id"),
            headers = bearerHeaders(token),
        ).body
        return toTrackInfo(root)
    }

    @Synchronized
    @Throws(Exception::class)
    private fun ensureToken(): String {
        val now = System.currentTimeMillis()
        val cached = accessToken
        if (cached != null && now < tokenExpireAtMs - 60_000) {
            return cached
        }

        val basic = Base64.getEncoder().encodeToString(
            "$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8)
        )

        val req = Request.Builder()
            .url(TOKEN_URL)
            .header("Authorization", "Basic $basic")
            .header("Accept", "application/json")
            .post(
                FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .build()
            )
            .build()

        val body = HttpText.sendStringResponse(req, timeoutMs = 10_000).body
        val root = MAPPER.readTree(body).asObject()
        val token = root.str("access_token").orEmpty()
        val expiresIn = root.int("expires_in") ?: 0

        accessToken = token
        tokenExpireAtMs = now + expiresIn * 1000L
        return token
    }

    private fun bearerHeaders(token: String): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Accept-Encoding" to "identity",
        "Authorization" to "Bearer $token",
    )

    private fun toTrackInfo(t: ObjectNode): TrackInfo {
        val id = t.str("id").orEmpty()
        val name = t.str("name").orEmpty()

        val artist = t.arr("artists")
            ?.takeIf { it.size() > 0 }
            ?.get(0)?.asObjectOpt()?.orElse(null)
            ?.str("name")
            .orEmpty()

        val albumObj = t.obj("album")
        val album = albumObj?.str("name").orEmpty()

        val cover = albumObj?.arr("images")
            ?.takeIf { it.size() > 0 }
            ?.get(0)?.asObjectOpt()?.orElse(null)
            ?.str("url")

        var url = t.obj("external_urls")?.str("spotify")
        if (url.isNullOrBlank()) {
            url = "https://open.spotify.com/track/$id"
        }

        val duration = t.long("duration_ms") ?: 0L
        return TrackInfo(platform(), id, name, artist, album, cover, url, duration)
    }

    private fun extractTrackIdFromLink(link: String?): String? {
        if (link.isNullOrBlank()) return null
        val m = TRACK_ID_RE.find(link.trim()) ?: return null
        return m.groupValues.getOrNull(1)
    }

}
