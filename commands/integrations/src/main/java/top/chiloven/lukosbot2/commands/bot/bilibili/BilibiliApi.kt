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
package top.chiloven.lukosbot2.commands.bot.bilibili

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.commands.bot.bilibili.schema.BilibiliViewDataDto
import top.chiloven.lukosbot2.commands.bot.bilibili.schema.VideoId
import top.chiloven.lukosbot2.http.requireSuccess
import top.chiloven.lukosbot2.http.toHttpStatusException
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.StringUtils.firstNonBlank

class BilibiliApi(
    private val http: HttpClient
) {

    private companion object {

        private const val API_CALL_TIMEOUT_MS = 8_000L
        private const val RELATION_TIMEOUT_MS = 6_000L
        private const val SHORT_LINK_TIMEOUT_MS = 6_000L

        private const val VIEW_API_URL = "https://api.bilibili.com/x/web-interface/view"
        private const val RELATION_API_URL = "https://api.bilibili.com/x/relation/stat"
        private const val HTML_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private val UA = "Mozilla/5.0 (compatible; ${Constants.UA}; +https://bilibili.com)"

    }

    private data class BilibiliViewResponse(
        val code: Int = 0,
        val message: String? = null,
        val data: BilibiliViewDataDto? = null
    )

    private data class BilibiliRelationResponse(
        val code: Int = 0,
        val data: RelationDataDto? = null
    ) {

        data class RelationDataDto(
            val mid: Long? = null,
            val follower: Long? = null
        )

    }

    private val noRedirectHttp: HttpClient = http.config {
        followRedirects = false
    }

    suspend fun resolveVideoId(input: String): VideoId? {
        VideoId.parse(input)?.let { return it }
        if (!input.startsWith("http", ignoreCase = true)) {
            return null
        }
        return resolveShortLink(input)
    }

    suspend fun getViewData(id: VideoId): BilibiliViewDataDto? {
        val response = http.get(VIEW_API_URL) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Accept, "application/json")
            when (id) {
                is VideoId.Bv -> parameter("bvid", id.bvid)
                is VideoId.Av -> parameter("aid", id.aid.toString())
            }

            timeout {
                requestTimeoutMillis = API_CALL_TIMEOUT_MS
            }
        }.requireSuccess()

        val text = response.bodyAsText()
        val res = JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            BilibiliViewResponse::class.java
        )
        return if (res.code != 0) null else res.data
    }

    suspend fun getFollowerCount(mid: Long): Long? = runCatching {
        val response = http.get(RELATION_API_URL) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Accept, "application/json")
            parameter("vmid", mid.toString())
            timeout {
                requestTimeoutMillis = RELATION_TIMEOUT_MS
            }
        }.requireSuccess()

        val text = response.bodyAsText()
        val res = JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            BilibiliRelationResponse::class.java
        )
        if (res.code != 0) return@runCatching null
        res.data?.follower
    }.getOrNull()

    private suspend fun resolveShortLink(url: String): VideoId? {
        VideoId.parse(url)?.let { return it }

        resolveLocation(url)?.let { location ->
            VideoId.parse(location)?.let { return it }
            resolveFinalVideoId(location)?.let { return it }
        }

        return resolveFinalVideoId(url)
    }

    private suspend fun resolveLocation(url: String): String? {
        val response = noRedirectHttp.get(url) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Accept, HTML_ACCEPT)
            timeout {
                requestTimeoutMillis = SHORT_LINK_TIMEOUT_MS
            }
        }

        if (response.status.value !in 300..399 && !response.status.isSuccess()) {
            throw response.toHttpStatusException()
        }

        return firstNonBlank(
            response.headers[HttpHeaders.Location],
            response.headers["location"],
            response.headers[HttpHeaders.ContentLocation],
            response.headers["content-location"],
        ).ifBlank { null }
    }

    private suspend fun resolveFinalVideoId(url: String): VideoId? {
        val response = http.get(url) {
            header(HttpHeaders.UserAgent, UA)
            header(HttpHeaders.Accept, HTML_ACCEPT)
            timeout {
                requestTimeoutMillis = SHORT_LINK_TIMEOUT_MS
            }
        }.requireSuccess()

        VideoId.parse(response.request.url.toString())?.let { return it }
        return VideoId.parse(response.bodyAsText())
    }

}
