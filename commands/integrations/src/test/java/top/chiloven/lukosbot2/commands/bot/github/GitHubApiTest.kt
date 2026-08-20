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
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.commands.bot.github.data.SearchParams
import top.chiloven.lukosbot2.util.HttpStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitHubApiTest {

    @Test
    fun `getUser parses user dto correctly`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(
                        "/users/octocat",
                        request.url.encodedPath
                    )
                    assertEquals(
                        "application/vnd.github.v3+json",
                        request.headers[HttpHeaders.Accept]
                    )
                    assertEquals(
                        "Bearer test_token",
                        request.headers[HttpHeaders.Authorization]
                    )

                    respond(
                        content = """
                            {
                              "login": "octocat",
                              "name": "The Octocat",
                              "html_url": "https://github.com/octocat",
                              "public_repos": 8,
                              "followers": 1000,
                              "following": 5
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = GitHubApi(client, "test_token")
        val user = api.getUser("octocat")

        assertEquals("octocat", user.login)
        assertEquals("The Octocat", user.name)
        assertEquals("https://github.com/octocat", user.htmlUrl)
        assertEquals(8, user.publicRepos)
        assertEquals(1000, user.followers)
        assertEquals(5, user.following)
        assertTrue(user.toReadableText().contains("The Octocat（octocat）"))
    }

    @Test
    fun `getRepo parses repository dto correctly`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/repos/Chiloven945/lukosbot2", request.url.encodedPath)
                    respond(
                        content = """
                            {
                              "full_name": "Chiloven945/lukosbot2",
                              "html_url": "https://github.com/Chiloven945/lukosbot2",
                              "language": "Kotlin",
                              "stargazers_count": 42,
                              "forks_count": 7,
                              "description": "Multi-platform bot framework"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = GitHubApi(client, null)
        val repo = api.getRepo("Chiloven945", "lukosbot2")

        assertEquals("Chiloven945/lukosbot2", repo.fullName)
        assertEquals("https://github.com/Chiloven945/lukosbot2", repo.htmlUrl)
        assertEquals("Kotlin", repo.language)
        assertEquals(42, repo.stargazersCount)
        assertEquals(7, repo.forksCount)
        assertEquals("Multi-platform bot framework", repo.description)
        assertTrue(repo.toReadableText().contains("Chiloven945/lukosbot2"))
    }

    @Test
    fun `searchRepos encodes query parameters and limits result`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/search/repositories", request.url.encodedPath)
                    assertEquals(true, request.url.parameters["q"]?.contains("lukosbot"))
                    assertEquals("stars", request.url.parameters["sort"])
                    assertEquals("desc", request.url.parameters["order"])
                    respond(
                        content = """
                            {
                              "total_count": 1,
                              "items": [
                                {
                                  "full_name": "Chiloven945/lukosbot2",
                                  "html_url": "https://github.com/Chiloven945/lukosbot2",
                                  "stargazers_count": 42
                                },
                                {
                                  "full_name": "other/bot",
                                  "html_url": "https://github.com/other/bot",
                                  "stargazers_count": 10
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = GitHubApi(client, null)
        val search = api.searchRepos(
            SearchParams(
                keywords = "lukosbot",
                top = 1,
                sort = "stars",
                order = "desc"
            )
        )

        assertEquals(1, search.totalCount)
        assertEquals(1, search.items.size)
        assertEquals("Chiloven945/lukosbot2", search.items[0].fullName)
        assertTrue(search.toReadableText().contains("42★"))
    }

    @Test
    fun `non 2xx response throws HttpStatusException`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content = """{"message":"Not Found"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val api = GitHubApi(client, null)
        val error = assertFailsWith<HttpStatusException> {
            api.getUser("nonexistent")
        }

        assertEquals(404, error.statusCode)
    }

}
