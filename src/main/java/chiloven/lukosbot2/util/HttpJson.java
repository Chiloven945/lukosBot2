package chiloven.lukosbot2.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

public final class HttpJson {
    private HttpJson() {
    }

    /**
     * Send a GET request and parse the JSON response.
     *
     * @param url              the request URL
     * @param headers          custom headers, can be null
     * @param connectTimeoutMs connection timeout in milliseconds
     * @param readTimeoutMs    read timeout in milliseconds
     * @return the parsed JSON object
     * @throws IOException if an I/O error occurs or the response code is >= 400
     */
    public static JsonObject get(String url,
                                 Map<String, String> headers,
                                 int connectTimeoutMs,
                                 int readTimeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        if (headers != null) {
            for (var e : headers.entrySet()) conn.setRequestProperty(e.getKey(), e.getValue());
        }
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.connect();

        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            if (code >= 400) {
                String msg = obj.has("message") ? obj.get("message").getAsString() : ("HTTP " + code);
                throw new IOException(msg);
            }
            return obj;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Build a query string from a map of parameters.
     * Returns an empty string if the map is null or empty.
     *
     * @param q the map of query parameters
     * @return the query string starting with '?'
     */
    public static String qs(Map<String, String> q) {
        if (q == null || q.isEmpty()) return "";
        StringJoiner sj = new StringJoiner("&", "?", "");
        for (var e : q.entrySet()) {
            sj.add(encode(e.getKey()) + "=" + encode(e.getValue()));
        }
        return sj.toString();
    }

    /**
     * URL-encode a string using UTF-8 encoding.
     *
     * @param s the string to encode
     * @return the encoded string
     */
    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
