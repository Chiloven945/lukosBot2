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
package top.chiloven.lukosbot2.commands.bot.github

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import top.chiloven.lukosbot2.commands.bot.github.data.GitHubRepo
import top.chiloven.lukosbot2.commands.bot.github.data.GitHubSearchResult
import top.chiloven.lukosbot2.commands.bot.github.data.GitHubUser
import top.chiloven.lukosbot2.commands.bot.github.data.SearchParams
import top.chiloven.lukosbot2.http.requireSuccess
import top.chiloven.lukosbot2.util.JsonUtils
import java.io.IOException

class GitHubApi(
    private val http: HttpClient,
    token: String? = null
) {

    private val token: String? = token?.takeIf { it.isNotBlank() }

    @Throws(IOException::class)
    suspend fun getUser(username: String): GitHubUser {
        val text = get("/users/$username", emptyMap())
        return JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            GitHubUser::class.java
        )
    }

    @Throws(IOException::class)
    suspend fun getRepo(owner: String, repo: String): GitHubRepo {
        val text = get("/repos/$owner/$repo", emptyMap())
        return JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            GitHubRepo::class.java
        )
    }

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
    suspend fun searchRepos(
        keywords: String,
        sort: String? = null,
        order: String? = null,
        language: String? = null,
        perPage: Int = 3
    ): GitHubSearchResult {
        val fullQ = buildString {
            append(keywords)
            language?.takeIf {
                it.isNotBlank()
            }?.let {
                append(" language:").append(it)
            }
        }

        val q = linkedMapOf<String, String>().apply {
            put("q", fullQ)
            putIfNotBlank("sort", sort)
            putIfNotBlank("order", order)
            if (perPage > 0) {
                put("per_page", perPage.coerceIn(1, 10).toString())
            }
        }

        val text = get("/search/repositories", q)
        val result = JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            GitHubSearchResult::class.java
        )
        return if (perPage > 0) {
            result.copy(items = result.items.take(perPage))
        } else result
    }

    @Throws(IOException::class)
    suspend fun searchRepos(params: SearchParams): GitHubSearchResult = searchRepos(
        params.keywords,
        params.sort,
        params.order,
        params.language,
        params.top
    )

    @Throws(IOException::class)
    private suspend fun get(
        path: String,
        query: Map<String, String>
    ): String {
        val response = http.get(BASE + path) {
            header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            for ((k, v) in query) {
                parameter(k, v)
            }
        }
        return response.requireSuccess().bodyAsText()
    }

    private fun MutableMap<String, String>.putIfNotBlank(key: String, value: String?) {
        value?.takeIf {
            it.isNotBlank()
        }?.let {
            put(key, it)
        }
    }

    private companion object {

        private const val BASE = "https://api.github.com"

    }

}
