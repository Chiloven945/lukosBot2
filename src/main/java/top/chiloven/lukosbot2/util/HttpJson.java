package top.chiloven.lukosbot2.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

public final class HttpJson {
    private static final int DEFAULT_READ_TIMEOUT = 10000;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpJson() {
    }

    public static HttpJson getHttpJson() {
        return new HttpJson();
    }

    /**
     * Sends a GET request and parses the JSON response.
     *
     * @param uri           the request URI
     * @param headers       additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON root
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     */
    public JsonElement getAny(
            URI uri,
            @Nullable Map<String, String> headers,
            int readTimeoutMs
    ) throws IOException {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("Accept", "application/json")
                    .GET();

            if (headers != null) headers.forEach(b::header);

            HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            String body = resp.body();

            JsonElement root;
            try {
                root = JsonParser.parseString(body);
            } catch (Exception e) {
                if (code >= 400) throw new IOException("HTTP " + code);
                throw new IOException("Response is not valid JSON");
            }

            if (code >= 400) throw new IOException(extractErrorMessage(root, code));
            return root;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    /**
     * Sends a GET request and parses the JSON response.
     *
     * @param uri     the request URI
     * @param headers additional headers, nullable
     * @return parsed JSON root
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     */
    public JsonElement getAny(
            URI uri,
            @Nullable Map<String, String> headers
    ) throws IOException {
        return getAny(uri, headers, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses the JSON response.
     *
     * @param uri           the request URI
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON root
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     */
    public JsonElement getAny(
            URI uri,
            int readTimeoutMs
    ) throws IOException {
        return getAny(uri, null, readTimeoutMs);
    }

    /**
     * Sends a GET request and parses the JSON response.
     *
     * @param uri the request URI
     * @return parsed JSON root
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     */
    public JsonElement getAny(
            URI uri
    ) throws IOException {
        return getAny(uri, null, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses the JSON response.
     *
     * @param uri           the request URI string
     * @param headers       additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON root
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     */
    public JsonElement getAny(
            String uri,
            @Nullable Map<String, String> headers,
            int readTimeoutMs
    ) throws IOException {
        return getAny(URI.create(uri), headers, readTimeoutMs);
    }

    /**
     * Sends a GET request and parses the JSON response.
     *
     * @param uri     the request URI string
     * @param headers additional headers, nullable
     * @return parsed JSON root
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     */
    public JsonElement getAny(
            String uri,
            @Nullable Map<String, String> headers
    ) throws IOException {
        return getAny(URI.create(uri), headers, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses the JSON response.
     *
     * @param uri           the request URI string
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON root
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     */
    public JsonElement getAny(
            String uri,
            int readTimeoutMs
    ) throws IOException {
        return getAny(URI.create(uri), null, readTimeoutMs);
    }

    /**
     * Sends a GET request and parses the JSON response.
     *
     * @param uri the request URI string
     * @return parsed JSON root
     * @throws IOException if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     */
    public JsonElement getAny(
            String uri
    ) throws IOException {
        return getAny(URI.create(uri), null, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses a JSON object response.
     *
     * @param uri           the request URI
     * @param headers       additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON object
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an object
     */
    public JsonObject getObject(
            URI uri,
            @Nullable Map<String, String> headers,
            int readTimeoutMs
    ) throws IOException {
        JsonElement root = getAny(uri, headers, readTimeoutMs);
        if (root != null && root.isJsonObject()) return root.getAsJsonObject();
        throw new IllegalArgumentException("Response JSON is not an object (it is " + typeOf(root) + ")");
    }

    /**
     * Sends a GET request and parses a JSON object response.
     *
     * @param uri     the request URI
     * @param headers additional headers, nullable
     * @return parsed JSON object
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an object
     */
    public JsonObject getObject(
            URI uri,
            @Nullable Map<String, String> headers
    ) throws IOException {
        return getObject(uri, headers, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses a JSON object response.
     *
     * @param uri           the request URI
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON object
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an object
     */
    public JsonObject getObject(
            URI uri,
            int readTimeoutMs
    ) throws IOException {
        return getObject(uri, null, readTimeoutMs);
    }

    /**
     * Sends a GET request and parses a JSON object response.
     *
     * @param uri the request URI
     * @return parsed JSON object
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an object
     */
    public JsonObject getObject(
            URI uri
    ) throws IOException {
        return getObject(uri, null, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses a JSON object response.
     *
     * @param uri           the request URI string
     * @param headers       additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON object
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an object
     */
    public JsonObject getObject(
            String uri,
            @Nullable Map<String, String> headers,
            int readTimeoutMs
    ) throws IOException {
        return getObject(URI.create(uri), headers, readTimeoutMs);
    }

    /**
     * Sends a GET request and parses a JSON object response.
     *
     * @param uri     the request URI string
     * @param headers additional headers, nullable
     * @return parsed JSON object
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an object
     */
    public JsonObject getObject(
            String uri,
            @Nullable Map<String, String> headers
    ) throws IOException {
        return getObject(URI.create(uri), headers, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses a JSON object response.
     *
     * @param uri           the request URI string
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON object
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an object
     */
    public JsonObject getObject(
            String uri,
            int readTimeoutMs
    ) throws IOException {
        return getObject(URI.create(uri), null, readTimeoutMs);
    }

    /**
     * Sends a GET request and parses a JSON object response.
     *
     * @param uri the request URI string
     * @return parsed JSON object
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an object
     */
    public JsonObject getObject(
            String uri
    ) throws IOException {
        return getObject(URI.create(uri), null, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses a JSON array response.
     *
     * @param uri           the request URI
     * @param headers       additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON array
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an array
     */
    public JsonArray getArray(
            URI uri,
            @Nullable Map<String, String> headers,
            int readTimeoutMs
    ) throws IOException {
        JsonElement root = getAny(uri, headers, readTimeoutMs);
        if (root != null && root.isJsonArray()) return root.getAsJsonArray();
        throw new IllegalArgumentException("Response JSON is not an array (it is " + typeOf(root) + ")");
    }

    /**
     * Sends a GET request and parses a JSON array response.
     *
     * @param uri     the request URI
     * @param headers additional headers, nullable
     * @return parsed JSON array
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an array
     */
    public JsonArray getArray(
            URI uri,
            @Nullable Map<String, String> headers
    ) throws IOException {
        return getArray(uri, headers, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses a JSON array response.
     *
     * @param uri           the request URI
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON array
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an array
     */
    public JsonArray getArray(
            URI uri,
            int readTimeoutMs
    ) throws IOException {
        return getArray(uri, null, readTimeoutMs);
    }

    /**
     * Sends a GET request and parses a JSON array response.
     *
     * @param uri the request URI
     * @return parsed JSON array
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an array
     */
    public JsonArray getArray(
            URI uri
    ) throws IOException {
        return getArray(uri, null, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses a JSON array response.
     *
     * @param uri           the request URI string
     * @param headers       additional headers, nullable
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON array
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an array
     */
    public JsonArray getArray(
            String uri,
            @Nullable Map<String, String> headers,
            int readTimeoutMs
    ) throws IOException {
        return getArray(URI.create(uri), headers, readTimeoutMs);
    }

    /**
     * Sends a GET request and parses a JSON array response.
     *
     * @param uri     the request URI string
     * @param headers additional headers, nullable
     * @return parsed JSON array
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an array
     */
    public JsonArray getArray(
            String uri,
            @Nullable Map<String, String> headers
    ) throws IOException {
        return getArray(URI.create(uri), headers, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Sends a GET request and parses a JSON array response.
     *
     * @param uri           the request URI string
     * @param readTimeoutMs read timeout in milliseconds
     * @return parsed JSON array
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an array
     */
    public JsonArray getArray(
            String uri,
            int readTimeoutMs
    ) throws IOException {
        return getArray(URI.create(uri), null, readTimeoutMs);
    }

    /**
     * Sends a GET request and parses a JSON array response.
     *
     * @param uri the request URI string
     * @return parsed JSON array
     * @throws IOException              if an I/O error occurs, the response code is &gt;= 400, or the response is not valid JSON
     * @throws IllegalArgumentException if the JSON root is not an array
     */
    public JsonArray getArray(
            String uri
    ) throws IOException {
        return getArray(URI.create(uri), null, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Builds a query string from a map.
     *
     * @param q query params
     * @return query string starting with '?', or empty string
     */
    public String buildQuery(Map<String, String> q) {
        if (q == null || q.isEmpty()) return "";
        StringJoiner sj = new StringJoiner("&", "?", "");
        for (var e : q.entrySet()) {
            sj.add(encode(e.getKey()) + "=" + encode(e.getValue()));
        }
        return sj.toString();
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String extractErrorMessage(JsonElement root, int code) {
        if (root != null && root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("message") && !obj.get("message").isJsonNull()) {
                try {
                    String m = obj.get("message").getAsString();
                    if (m != null && !m.isBlank()) return m;
                } catch (Exception ignored) {
                }
            }
            for (String k : new String[]{"error", "detail"}) {
                if (obj.has(k) && !obj.get(k).isJsonNull()) {
                    try {
                        String m = obj.get(k).getAsString();
                        if (m != null && !m.isBlank()) return m;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return "HTTP " + code;
    }

    private String typeOf(JsonElement el) {
        if (el == null) return "null";
        if (el.isJsonObject()) return "object";
        if (el.isJsonArray()) return "array";
        if (el.isJsonPrimitive()) return "primitive";
        if (el.isJsonNull()) return "null";
        return "unknown";
    }
}
