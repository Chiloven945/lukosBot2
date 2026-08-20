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
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.jspecify.annotations.NonNull
import top.chiloven.lukosbot2.commands.bot.music.MusicPlatform
import top.chiloven.lukosbot2.commands.bot.music.TrackInfo
import top.chiloven.lukosbot2.http.requireSuccess
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.StringUtils.firstNonBlank

class SoundCloudMusicProvider(
    private val http: HttpClient,
    private val clientId: String
) : IMusicProvider {

    private companion object {

        private const val API_BASE_V2 = "https://api-v2.soundcloud.com"

    }

    private data class SoundCloudSearchResponse(
        val collection: List<SoundCloudTrackDto> = emptyList()
    )

    private data class SoundCloudTrackDto(
        val id: String? = null,
        val title: String? = null,
        val publisherMetadata: PublisherMetadataDto? = null,
        val user: UserDto? = null,
        val sets: List<String>? = null,
        val playlist: String? = null,
        val artworkUrl: String? = null,
        val permalinkUrl: String? = null,
        val fullDuration: Long? = null
    ) {

        data class PublisherMetadataDto(
            val artist: String? = null,
            val albumTitle: String? = null,
            val releaseTitle: String? = null
        )

        data class UserDto(
            val username: String? = null
        )

    }

    override fun platform(): @NonNull MusicPlatform = MusicPlatform.SOUNDCLOUD

    @Throws(Exception::class)
    override suspend fun searchTrack(query: String): TrackInfo? {
        val response = http.get("$API_BASE_V2/search/tracks") {
            parameter("q", query)
            parameter("client_id", clientId)
            parameter("limit", "1")
        }.requireSuccess()

        val text = response.bodyAsText()
        val dto = JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            SoundCloudSearchResponse::class.java
        )
        val t = dto.collection.firstOrNull() ?: return null
        return toTrackInfo(t)
    }

    @Throws(Exception::class)
    override suspend fun resolveLink(link: String): TrackInfo {
        val response = http.get("$API_BASE_V2/resolve") {
            parameter("url", link)
            parameter("client_id", clientId)
        }.requireSuccess()

        val text = response.bodyAsText()
        val t = JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            SoundCloudTrackDto::class.java
        )
        return toTrackInfo(t)
    }

    private fun toTrackInfo(t: SoundCloudTrackDto): TrackInfo {
        val id = t.id.orEmpty()
        val title = t.title.orEmpty()

        val artist = firstNonBlank(
            t.publisherMetadata?.artist,
            t.user?.username
        )

        val album = firstNonBlank(
            t.publisherMetadata?.albumTitle,
            t.publisherMetadata?.releaseTitle,
            t.playlist,
            t.sets?.firstOrNull()
        )

        val cover = t.artworkUrl
        val url = t.permalinkUrl
        val duration = t.fullDuration ?: 0L

        return TrackInfo(
            platform(),
            id,
            title,
            artist,
            album,
            cover,
            url,
            duration
        )
    }

}
