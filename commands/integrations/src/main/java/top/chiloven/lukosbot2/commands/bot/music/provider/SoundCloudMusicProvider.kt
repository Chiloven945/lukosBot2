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

import org.jspecify.annotations.NonNull
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.commands.bot.music.MusicPlatform
import top.chiloven.lukosbot2.commands.bot.music.TrackInfo
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.long
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.StringUtils.firstNonBlank
import java.net.URI

class SoundCloudMusicProvider(
    private val clientId: String
) : IMusicProvider {

    private companion object {

        private const val API_BASE_V2 = "https://api-v2.soundcloud.com"

    }

    override fun platform(): @NonNull MusicPlatform = MusicPlatform.SOUNDCLOUD

    @Throws(Exception::class)
    override fun searchTrack(query: String): TrackInfo? {
        val root = HttpJson.getObjectResponse(
            uri = URI("$API_BASE_V2/search/tracks"),
            params = mapOf(
                "q" to query,
                "client_id" to clientId,
                "limit" to "1",
            ),
        ).body
        val collection = root.arr("collection")
        if (collection == null || collection.size() == 0) return null

        val t = collection[0].asObject()
        return toTrackInfo(t)
    }

    @Throws(Exception::class)
    override fun resolveLink(link: String): TrackInfo {
        val t = HttpJson.getObjectResponse(
            uri = URI("$API_BASE_V2/resolve"),
            params = mapOf(
                "url" to link,
                "client_id" to clientId,
            ),
        ).body
        return toTrackInfo(t)
    }

    private fun toTrackInfo(t: ObjectNode): TrackInfo {
        val id = t.str("id").orEmpty()
        val title = t.str("title").orEmpty()

        val pub = t.obj("publisher_metadata")
        val user = t.obj("user")

        val artist = firstNonBlank(
            pub?.str("artist"),
            user?.str("username")
        )

        val fromSets = t.arr("sets")
            ?.takeIf { it.size() > 0 && it[0].isValueNode }
            ?.get(0)?.asString()
            ?.takeIf { it.isNotBlank() }

        val album = firstNonBlank(
            pub?.str("album_title"),
            pub?.str("release_title"),
            t.str("playlist"),
            fromSets
        )

        val cover = t.str("artwork_url")
        val url = t.str("permalink_url")

        val duration = t.long("full_duration")!!

        return TrackInfo(platform(), id, title, artist, album, cover, url, duration)
    }

}
