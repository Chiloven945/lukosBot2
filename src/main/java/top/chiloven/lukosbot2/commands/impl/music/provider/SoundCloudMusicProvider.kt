package top.chiloven.lukosbot2.commands.impl.music.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.chiloven.lukosbot2.commands.impl.music.MusicPlatform;
import top.chiloven.lukosbot2.commands.impl.music.TrackInfo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

public class SoundCloudMusicProvider implements IMusicProvider {

    private static final String API_BASE_V2 = "https://api-v2.soundcloud.com";

    private final String clientId;

    public SoundCloudMusicProvider(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public MusicPlatform platform() {
        return MusicPlatform.SOUNDCLOUD;
    }

    @Override
    public TrackInfo searchTrack(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = API_BASE_V2 + "/search/tracks?q=" + encoded + "&client_id=" + clientId + "&limit=1";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("SoundCloud search error: " + resp.body());
        }

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray collection = root.getAsJsonArray("collection");
        if (collection == null || collection.isEmpty()) return null;

        JsonObject t = collection.get(0).getAsJsonObject();
        return toTrackInfo(t);
    }

    @Override
    public TrackInfo resolveLink(String link) throws Exception {
        String encoded = URLEncoder.encode(link, StandardCharsets.UTF_8);
        String url = API_BASE_V2 + "/resolve?url=" + encoded + "&client_id=" + clientId;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("SoundCloud resolve error: " + resp.body());
        }

        JsonObject t = JsonParser.parseString(resp.body()).getAsJsonObject();
        return toTrackInfo(t);
    }

    private TrackInfo toTrackInfo(JsonObject t) {
        String id = t.get("id").getAsString();
        String title = t.get("title").getAsString();

        // ----- artist: publisher_metadata.artist > user.username -----
        JsonObject pub = t.has("publisher_metadata") && t.get("publisher_metadata").isJsonObject()
                ? t.getAsJsonObject("publisher_metadata")
                : null;
        JsonObject user = t.has("user") && t.get("user").isJsonObject()
                ? t.getAsJsonObject("user")
                : null;

        String artist = Stream.of(
                        pub != null && pub.has("artist") && !pub.get("artist").isJsonNull()
                                ? pub.get("artist").getAsString()
                                : null,
                        user != null && user.has("username") && !user.get("username").isJsonNull()
                                ? user.get("username").getAsString()
                                : null
                )
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("");

        String fromSets = null;
        if (t.has("sets") && t.get("sets").isJsonArray()) {
            JsonArray sets = t.getAsJsonArray("sets");
            if (!sets.isEmpty() && sets.get(0).isJsonPrimitive()) {
                String v = sets.get(0).getAsString();
                if (v != null && !v.isBlank()) {
                    fromSets = v;
                }
            }
        }

        String album = Stream.of(
                        pub != null && pub.has("album_title") && !pub.get("album_title").isJsonNull()
                                ? pub.get("album_title").getAsString()
                                : null,
                        pub != null && pub.has("release_title") && !pub.get("release_title").isJsonNull()
                                ? pub.get("release_title").getAsString()
                                : null,
                        t.has("playlist") && !t.get("playlist").isJsonNull()
                                ? t.get("playlist").getAsString()
                                : null,
                        fromSets
                )
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("");

        // ----- cover -----
        String cover = (t.has("artwork_url") && !t.get("artwork_url").isJsonNull())
                ? t.get("artwork_url").getAsString()
                : null;

        // ----- url -----
        String url = (t.has("permalink_url") && !t.get("permalink_url").isJsonNull())
                ? t.get("permalink_url").getAsString()
                : null;

        // ----- duration -----
        long duration = 0L;
        if (t.has("full_duration") && !t.get("full_duration").isJsonNull()) {
            duration = t.get("full_duration").getAsLong();
        } else if (t.has("duration") && !t.get("duration").isJsonNull()) {
            duration = t.get("duration").getAsLong();
        }

        return new TrackInfo(platform(), id, title, artist, album, cover, url, duration);
    }
}
