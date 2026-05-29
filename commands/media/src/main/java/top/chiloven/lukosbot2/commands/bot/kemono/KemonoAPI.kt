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

import org.apache.logging.log4j.LogManager
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.commands.bot.kemono.schema.Service
import top.chiloven.lukosbot2.util.HttpJson
import java.io.IOException
import java.net.URI

object KemonoAPI {

    private val log = LogManager.getLogger(KemonoAPI::class.java)

    private val API = URI.create("https://kemono.cr/api/")

    /** Kemono only accept "text/css"*/
    private val HEADER = mapOf("Accept" to "text/css")

    private fun resolve(path: String): URI {
        val uri: URI = API.resolve(path)
        log.debug("Kemono API request: {}", uri)
        return uri
    }

    @Throws(IOException::class)
    fun getSpecificPost(
        service: Service,
        creatorId: String,
        postId: String
    ): ObjectNode = HttpJson.getObject(
        resolve("v1/$service/user/$creatorId/post/$postId"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getCreatorProfile(
        service: Service,
        creatorId: String
    ): ObjectNode = HttpJson.getObject(
        resolve("v1/$service/user/$creatorId/profile"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getCreatorLinks(
        service: Service,
        creatorId: String
    ): ArrayNode = HttpJson.getArray(
        resolve("v1/$service/user/$creatorId/links"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getCreatorPosts(
        service: Service,
        creatorId: String
    ): ArrayNode = HttpJson.getArray(
        resolve("v1/$service/user/$creatorId/posts"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getDiscordChannelPost(channelId: String): ArrayNode = HttpJson.getArray(
        resolve("v1/discord/channel/$channelId"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getDiscordServerChannel(channelId: String): ArrayNode = HttpJson.getArray(
        resolve("v1/discord/channel/lookup/$channelId"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getFileFromHash(hash: String): ObjectNode = HttpJson.getObject(
        resolve("v1/search_hash/$hash"),
        headers = HEADER
    )

    @Throws(IOException::class)
    fun getPostFromServicePost(
        service: Service,
        servicePostId: String
    ): ObjectNode = HttpJson.getObject(
        resolve("v1/$service/post/$servicePostId"),
        headers = HEADER
    )

}
