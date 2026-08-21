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
package top.chiloven.lukosbot2.commands.bot.e621

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import top.chiloven.lukosbot2.commands.bot.e621.schema.Artist
import top.chiloven.lukosbot2.commands.bot.e621.schema.Post
import top.chiloven.lukosbot2.http.requireSuccess
import top.chiloven.lukosbot2.util.JsonUtils
import java.io.IOException
import java.net.URI

class E621Api(
    private val http: HttpClient
) {

    companion object {

        val API: URI = URI.create("https://e621.net")

    }

    @Throws(IOException::class)
    suspend fun getArtists(
        limit: Int? = null,
        page: Int? = null,
        searchId: Int? = null,
        searchOrder: String? = null,
        searchName: String? = null,
        searchGroupName: String? = null,
        searchAnyOtherNameLike: String? = null,
        searchAnyNameMatches: String? = null,
        searchAnyNameOrUrlMatches: String? = null,
        searchUrlMatches: String? = null,
        searchCreatorName: String? = null,
        searchCreatorId: String? = null,
        searchHasTag: String? = null,
        searchIsLinked: String? = null,
        searchLinkedUserId: Int? = null,
        searchLinkedUserName: String? = null
    ): List<Artist> {
        val params = mapOf(
            "limit" to limit,
            "page" to page,
            "search[id]" to searchId,
            "search[order]" to searchOrder,
            "search[name]" to searchName,
            "search[group_name]" to searchGroupName,
            "search[any_other_name_like]" to searchAnyOtherNameLike,
            "search[any_name_matches]" to searchAnyNameMatches,
            "search[any_name_or_url_matches]" to searchAnyNameOrUrlMatches,
            "search[url_matches]" to searchUrlMatches,
            "search[creator_name]" to searchCreatorName,
            "search[creator_id]" to searchCreatorId,
            "search[has_tag]" to searchHasTag,
            "search[is_linked]" to searchIsLinked,
            "search[linked_user_id]" to searchLinkedUserId,
            "search[linked_user_name]" to searchLinkedUserName
        ).mapNotNull { (key, value) ->
            value?.let {
                key to it.toString()
            }
        }.toMap()

        val response = http.get(API.resolve("artists.json").toString()) {
            params.forEach { (k, v) ->
                parameter(k, v)
            }
        }.requireSuccess()

        val text = response.bodyAsText()
        val node = JsonUtils.MAPPER.readTree(text)
        return if (node.isArray) {
            Artist.fromJsonArray(node.asArray())
        } else {
            emptyList()
        }
    }

    @Throws(IOException::class)
    suspend fun getArtistsXIdOrName(idOrName: String): Artist {
        val response = http.get(API.resolve("artists/$idOrName.json").toString()).requireSuccess()
        val text = response.bodyAsText()
        val node = JsonUtils.MAPPER.readTree(text).asObject()
        return Artist.fromJsonObject(node)
    }

    @Throws(IOException::class)
    suspend fun getPosts(
        limit: Int? = null,
        page: Int? = null,
        tags: String? = null,
        md5: String? = null,
        random: String? = null,
    ): List<Post> {
        val params = mapOf(
            "limit" to limit,
            "page" to page,
            "tags" to tags,
            "md5" to md5,
            "random" to random
        ).mapNotNull { (key, value) -> value?.let { key to it.toString() } }.toMap()

        val response = http.get(API.resolve("posts.json").toString()) {
            params.forEach { (k, v) ->
                parameter(k, v)
            }
        }.requireSuccess()

        val text = response.bodyAsText()
        val root = JsonUtils.MAPPER.readTree(text).asObject()
        root.get("posts")?.let { postsNode ->
            if (postsNode.isArray) {
                return Post.fromJsonArray(postsNode.asArray())
            }
        }

        root.get("post")?.let { postNode ->
            if (postNode.isObject) {
                return listOf(Post.fromJsonObject(postNode.asObject()))
            }
        }

        return emptyList()
    }

    @Throws(IOException::class)
    suspend fun getPostsXRandom(tags: String? = null): Post? {
        val response = http.get(API.resolve("posts/random.json").toString()) {
            tags?.let { parameter("tags", it) }
        }.requireSuccess()

        val text = response.bodyAsText()
        val root = JsonUtils.MAPPER.readTree(text).asObject()
        return root.get("post")?.asObjectOpt()?.orElse(null)?.let(Post::fromJsonObject)
    }

    @Throws(IOException::class)
    suspend fun getPostsXId(id: String): Post? {
        val response = http.get(API.resolve("posts/$id.json").toString()).requireSuccess()
        val text = response.bodyAsText()
        val root = JsonUtils.MAPPER.readTree(text).asObject()
        return root.get("post")?.asObjectOpt()?.orElse(null)?.let(Post::fromJsonObject)
    }

}
