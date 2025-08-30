package chiloven.lukosbot2.commands.github;

import chiloven.lukosbot2.util.http.HttpJson;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public final class GitHubApi {
    private static final String BASE = "https://api.github.com";
    private final int connTimeoutMs = 6000, readTimeoutMs = 10000;
    private final Gson gson = new Gson();
    private final String token; // 可为 null

    public GitHubApi(String token) {
        this.token = (token == null || token.isBlank()) ? null : token;
    }

    private static String buildQuery(Map<String, String> q) {
        if (q == null || q.isEmpty()) return "";
        StringJoiner sj = new StringJoiner("&", "?", "");
        for (var e : q.entrySet()) {
            sj.add(encode(e.getKey()) + "=" + encode(e.getValue()));
        }
        return sj.toString();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public JsonObject getUser(String username) throws IOException {
        return get("/users/" + username, Map.of());
    }

    public JsonObject getRepo(String owner, String repo) throws IOException {
        return get("/repos/" + owner + "/" + repo, Map.of());
    }

    /**
     * Search repositories on GitHub with various parameters.
     *
     * @param keywords Search keywords
     * @param sort     (can be "stars", "forks", "help-wanted-issues", or "updated")
     * @param order    ("asc" or "desc")
     * @param language Programming language filter (optional)
     * @param perPage  Number of results per page (max 10)
     * @return JsonObject containing search results
     * @throws IOException if the request fails or there is a network error
     */
    public JsonObject searchRepos(String keywords, String sort, String order, String language, int perPage)
            throws IOException {
        LinkedHashMap<String, String> q = new LinkedHashMap<>();
        String fullQ = keywords + ((language != null && !language.isBlank()) ? " language:" + language : "");
        q.put("q", fullQ);

        if (sort != null && !sort.isBlank()) q.put("sort", sort);
        if (order != null && !order.isBlank()) q.put("order", order);
        if (perPage > 0) q.put("per_page", String.valueOf(Math.min(perPage, 10)));

        return get("/search/repositories", q);
    }

    /**
     * The get method to make HTTP GET requests to GitHub API.
     *
     * @param path  API path, e.g. "/users/{username}"
     * @param query Query parameters as a map
     * @return JsonObject containing the response
     * @throws IOException if the request fails or there is a network error
     */
    private JsonObject get(String path, Map<String, String> query) throws IOException {
        String url = BASE + path + HttpJson.qs(query);
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Accept", "application/vnd.github.v3+json");
        if (token != null) headers.put("Authorization", "Bearer " + token);
        return HttpJson.get(url, headers, connTimeoutMs, readTimeoutMs);
    }
}
