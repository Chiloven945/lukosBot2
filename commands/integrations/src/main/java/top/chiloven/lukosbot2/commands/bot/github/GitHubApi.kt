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

import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.HttpJson
import java.io.IOException

class GitHubApi(token: String?) {

    private val token: String? = token?.takeIf { it.isNotBlank() }

    @Throws(IOException::class)
    fun getUser(username: String): ObjectNode =
        get("/users/$username", emptyMap())

    @Throws(IOException::class)
    fun getRepo(owner: String, repo: String): ObjectNode =
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
    ): ObjectNode {
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
    private fun get(path: String, query: Map<String, String>): ObjectNode {
        val headers = linkedMapOf("Accept" to "application/vnd.github.v3+json").apply {
            token?.let { put("Authorization", "Bearer $it") }
        }

        return HttpJson.getObject(
            uri = BASE + path,
            params = query,
            headers = headers
        )
    }

    private fun MutableMap<String, String>.putIfNotBlank(key: String, value: String?) {
        value?.takeIf { it.isNotBlank() }?.let { put(key, it) }
    }

    private companion object {

        private const val BASE = "https://api.github.com"

    }

}
