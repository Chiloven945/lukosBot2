package top.chiloven.lukosbot2.commands.impl.music.provider

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.chiloven.lukosbot2.commands.impl.music.MusicPlatform
import top.chiloven.lukosbot2.commands.impl.music.TrackInfo
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.long
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*

class SpotifyMusicProvider(
    private val clientId: String,
    private val clientSecret: String
) : IMusicProvider {

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpireAtMs: Long = 0L

    override fun platform(): MusicPlatform = MusicPlatform.SPOTIFY

    @Throws(Exception::class)
    override fun searchTrack(query: String): TrackInfo? {
        val token = ensureToken()
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = "$API_BASE/search?q=$encoded&type=track&limit=1"

        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        val resp = IMusicProvider.HTTP.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            throw RuntimeException("Spotify search error: ${resp.body()}")
        }

        val root = JsonParser.parseString(resp.body()).asJsonObject
        val tracks = root.obj("tracks") ?: return null
        val items = tracks.arr("items") ?: return null
        if (items.size() == 0) return null

        return toTrackInfo(items[0].asJsonObject)
    }

    @Throws(Exception::class)
    override fun resolveLink(link: String): TrackInfo? {
        val id = extractTrackIdFromLink(link)?.takeIf { it.isNotBlank() } ?: return null

        val token = ensureToken()
        val url = "$API_BASE/tracks/$id"

        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        val resp = IMusicProvider.HTTP.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            throw RuntimeException("Spotify track error: ${resp.body()}")
        }

        val root = JsonParser.parseString(resp.body()).asJsonObject
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

        val req = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URL))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Basic $basic")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
            .build()

        val resp = IMusicProvider.HTTP.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            throw RuntimeException("Spotify token error: ${resp.body()}")
        }

        val root = JsonParser.parseString(resp.body()).asJsonObject
        val token = root.str("access_token").orEmpty()
        val expiresIn = root.int("expires_in") ?: 0

        accessToken = token
        tokenExpireAtMs = now + expiresIn * 1000L
        return token
    }

    private fun toTrackInfo(t: JsonObject): TrackInfo {
        val id = t.str("id").orEmpty()
        val name = t.str("name").orEmpty()

        val artist = t.arr("artists")
            ?.takeIf { it.size() > 0 }
            ?.get(0)?.asJsonObject
            ?.str("name")
            .orEmpty()

        val albumObj = t.obj("album")
        val album = albumObj?.str("name").orEmpty()

        val cover = albumObj?.arr("images")
            ?.takeIf { it.size() > 0 }
            ?.get(0)?.asJsonObject
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

    private companion object {
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val API_BASE = "https://api.spotify.com/v1"
        private val TRACK_ID_RE = Regex("""/track/([^?#\s/]+)""")
    }
}
