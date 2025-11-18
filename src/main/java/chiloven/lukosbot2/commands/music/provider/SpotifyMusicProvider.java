package chiloven.lukosbot2.commands.music.provider;

import chiloven.lukosbot2.commands.music.MusicPlatform;
import chiloven.lukosbot2.commands.music.TrackInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpotifyMusicProvider implements MusicProvider {

    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String API_BASE = "https://api.spotify.com/v1";

    private final String clientId;
    private final String clientSecret;
    private volatile String accessToken;
    private volatile long tokenExpireAtMs;

    public SpotifyMusicProvider(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public MusicPlatform platform() {
        return MusicPlatform.SPOTIFY;
    }

    @Override
    public TrackInfo searchTrack(String query) throws Exception {
        String token = ensureToken();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = API_BASE + "/search?q=" + encoded + "&type=track&limit=1";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Spotify search error: " + resp.body());
        }

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();

        JsonObject tracks = root.getAsJsonObject("tracks");
        if (tracks == null) return null;

        JsonArray items = tracks.getAsJsonArray("items");
        if (items == null || items.isEmpty()) return null;

        JsonObject t = items.get(0).getAsJsonObject();
        return toTrackInfo(t);
    }

    @Override
    public TrackInfo resolveLink(String link) throws Exception {
        String id = extractTrackIdFromLink(link);
        if (id == null || id.isBlank()) {
            return null;
        }

        String token = ensureToken();
        String url = API_BASE + "/tracks/" + id;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Spotify track error: " + resp.body());
        }

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        return toTrackInfo(root);
    }

    private String ensureToken() throws Exception {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < tokenExpireAtMs - 60_000) { // 预留 60s
            return accessToken;
        }
        String auth = clientId + ":" + clientSecret;
        String basic = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Spotify token error: " + resp.body());
        }

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        String token = root.get("access_token").getAsString();
        int expiresIn = root.get("expires_in").getAsInt(); // 秒

        this.accessToken = token;
        this.tokenExpireAtMs = now + expiresIn * 1000L;
        return token;
    }

    private TrackInfo toTrackInfo(JsonObject t) {
        String id = t.get("id").getAsString();
        String name = t.get("name").getAsString();

        String artist = "";
        JsonArray artists = t.getAsJsonArray("artists");
        if (artists != null && !artists.isEmpty()) {
            JsonObject a0 = artists.get(0).getAsJsonObject();
            artist = a0.get("name").getAsString();
        }

        String album = "";
        String cover = null;
        JsonObject albumObj = t.getAsJsonObject("album");
        if (albumObj != null) {
            album = albumObj.get("name").getAsString();
            JsonArray imgs = albumObj.getAsJsonArray("images");
            if (imgs != null && !imgs.isEmpty()) {
                cover = imgs.get(0).getAsJsonObject().get("url").getAsString();
            }
        }

        String url = null;
        if (t.has("external_urls") && t.get("external_urls").isJsonObject()) {
            JsonObject ext = t.getAsJsonObject("external_urls");
            if (ext.has("spotify") && !ext.get("spotify").isJsonNull()) {
                url = ext.get("spotify").getAsString();
            }
        }
        if (url == null || url.isBlank()) {
            url = "https://open.spotify.com/track/" + id;
        }

        long duration = t.get("duration_ms").getAsLong();

        return new TrackInfo(platform(), id, name, artist, album, cover, url, duration);
    }

    private String extractTrackIdFromLink(String link) {
        if (link == null) return null;
        Matcher m = Pattern.compile("/track/([^?#\\s/]+)").matcher(link.trim());
        return m.find() ? m.group(1) : null;
    }
}
