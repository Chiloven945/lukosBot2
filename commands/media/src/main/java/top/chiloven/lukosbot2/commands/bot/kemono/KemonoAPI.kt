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
package top.chiloven.lukosbot2.commands.bot.kemono

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.apache.logging.log4j.LogManager
import top.chiloven.lukosbot2.commands.bot.kemono.schema.*
import top.chiloven.lukosbot2.http.requireSuccess
import top.chiloven.lukosbot2.util.JsonUtils
import java.io.IOException
import java.net.URI

class KemonoAPI(
    private val http: HttpClient
) {

    private val log = LogManager.getLogger(KemonoAPI::class.java)

    data class KemonoServicePostMapping(
        val artistId: String,
        val postId: String
    )

    private companion object {

        private val API = URI.create("https://kemono.cr/api/")

        /** Kemono only accept "text/css"*/
        private val HEADER = mapOf("Accept" to "text/css")

    }

    private fun resolve(path: String): URI {
        val uri: URI = API.resolve(path)
        log.debug("Kemono API request: {}", uri)
        return uri
    }

    @Throws(IOException::class)
    suspend fun getSpecificPost(
        service: Service,
        creatorId: String,
        postId: String
    ): Post {
        val response = http.get(resolve("v1/$service/user/$creatorId/post/$postId").toString()) {
            HEADER.forEach { (k, v) -> header(k, v) }
        }.requireSuccess()
        val text = response.bodyAsText()
        val node = JsonUtils.MAPPER.readTree(text).asObject()
        return Post.fromSpecificPost(node)
    }

    @Throws(IOException::class)
    suspend fun getCreatorProfile(
        service: Service,
        creatorId: String
    ): Creator.Profile {
        val response = http.get(resolve("v1/$service/user/$creatorId/profile").toString()) {
            HEADER.forEach { (k, v) -> header(k, v) }
        }.requireSuccess()
        val text = response.bodyAsText()
        val node = JsonUtils.MAPPER.readTree(text).asObject()
        return JsonUtils.snakeTreeToValue(node, Creator.Profile::class.java)
    }

    @Throws(IOException::class)
    suspend fun getCreatorPosts(
        service: Service,
        creatorId: String
    ): List<PostSimple> {
        val response = http.get(resolve("v1/$service/user/$creatorId/posts").toString()) {
            HEADER.forEach { (k, v) -> header(k, v) }
        }.requireSuccess()
        val text = response.bodyAsText()
        val node = JsonUtils.MAPPER.readTree(text)

        return if (node.isArray) {
            PostSimple.fromArraySimplePost(node.asArray())
        } else {
            emptyList()
        }
    }

    @Throws(IOException::class)
    suspend fun getFileFromHash(hash: String): HashSearchFile {
        val response = http.get(resolve("v1/search_hash/$hash").toString()) {
            HEADER.forEach { (k, v) -> header(k, v) }
        }.requireSuccess()
        val text = response.bodyAsText()
        val node = JsonUtils.MAPPER.readTree(text).asObject()
        return HashSearchFile.fromJsonObject(node)
    }

    @Throws(IOException::class)
    suspend fun getPostFromServicePost(
        service: Service,
        servicePostId: String
    ): KemonoServicePostMapping {
        val response = http.get(resolve("v1/$service/post/$servicePostId").toString()) {
            HEADER.forEach { (k, v) -> header(k, v) }
        }.requireSuccess()
        val text = response.bodyAsText()
        val node = JsonUtils.MAPPER.readTree(text).asObject()
        val artistId = node.get("artist_id")?.asString().orEmpty()
        val postId = node.get("post_id")?.asString().orEmpty()
        return KemonoServicePostMapping(artistId, postId)
    }

}
