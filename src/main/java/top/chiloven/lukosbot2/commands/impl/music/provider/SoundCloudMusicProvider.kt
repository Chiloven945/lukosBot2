package top.chiloven.lukosbot2.commands.impl.music.provider

import org.jspecify.annotations.NonNull
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.commands.impl.music.MusicPlatform
import top.chiloven.lukosbot2.commands.impl.music.TrackInfo
import top.chiloven.lukosbot2.util.JsonUtils.MAPPER
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.long
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.StringUtils.firstNonBlank
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class SoundCloudMusicProvider(
    private val clientId: String
) : IMusicProvider {

    private companion object {

        private const val API_BASE_V2 = "https://api-v2.soundcloud.com"

    }

    override fun platform(): @NonNull MusicPlatform = MusicPlatform.SOUNDCLOUD

    @Throws(Exception::class)
    override fun searchTrack(query: String): TrackInfo? {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = "$API_BASE_V2/search/tracks?q=$encoded&client_id=$clientId&limit=1"

        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val resp = IMusicProvider.HTTP.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            throw RuntimeException("SoundCloud search error: ${resp.body()}")
        }

        val root = MAPPER.readTree(resp.body()).asObject()
        val collection = root.arr("collection")
        if (collection == null || collection.size() == 0) return null

        val t = collection[0].asObject()
        return toTrackInfo(t)
    }

    @Throws(Exception::class)
    override fun resolveLink(link: String): TrackInfo {
        val encoded = URLEncoder.encode(link, StandardCharsets.UTF_8)
        val url = "$API_BASE_V2/resolve?url=$encoded&client_id=$clientId"

        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val resp = IMusicProvider.HTTP.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            throw RuntimeException("SoundCloud resolve error: ${resp.body()}")
        }

        val t = MAPPER.readTree(resp.body()).asObject()
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
