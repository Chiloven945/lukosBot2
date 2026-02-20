package top.chiloven.lukosbot2.commands.impl.github

import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.HttpJson
import java.io.IOException

class GitHubApi(token: String?) {
    private val token: String? = token?.takeIf { it.isNotBlank() }

    @Throws(IOException::class)
    fun getUser(username: String): JsonObject =
        get("/users/$username", emptyMap())

    @Throws(IOException::class)
    fun getRepo(owner: String, repo: String): JsonObject =
        get("/repos/$owner/$repo", emptyMap())

    /**
     * Search repositories on GitHub with various parameters.
     *
     * @param keywords Search keywords
     * @param sort     "stars", "forks", "help-wanted-issues", "updated" (optional)
     * @param order    "asc" or "desc" (optional)
     * @param language Programming language filter (optional)
     * @param perPage  Number of results per page (max 10)
     */
    @Throws(IOException::class)
    fun searchRepos(
        keywords: String,
        sort: String?,
        order: String?,
        language: String?,
        perPage: Int
    ): JsonObject {
        val fullQ = buildString {
            append(keywords)
            language?.takeIf { it.isNotBlank() }?.let { append(" language:").append(it) }
        }

        val q = linkedMapOf<String, String>().apply {
            put("q", fullQ)
            putIfNotBlank("sort", sort)
            putIfNotBlank("order", order)
            if (perPage > 0) put("per_page", perPage.coerceIn(1, 10).toString())
        }

        return get("/search/repositories", q)
    }

    @Throws(IOException::class)
    private fun get(path: String, query: Map<String, String>): JsonObject {
        val url = BASE + path + HttpJson.buildQuery(query)

        val headers = linkedMapOf(
            "Accept" to "application/vnd.github.v3+json"
        ).apply {
            token?.let { put("Authorization", "Bearer $it") }
        }

        return HttpJson.getObject(url, headers)
    }

    private fun MutableMap<String, String>.putIfNotBlank(key: String, value: String?) {
        value?.takeIf { it.isNotBlank() }?.let { put(key, it) }
    }

    private companion object {
        private const val BASE = "https://api.github.com"
    }
}
