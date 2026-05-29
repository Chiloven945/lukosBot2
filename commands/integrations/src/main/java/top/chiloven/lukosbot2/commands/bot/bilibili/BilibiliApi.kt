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

import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Service
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.commands.bot.bilibili.schema.VideoId
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.long
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.OkHttpUtils
import top.chiloven.lukosbot2.util.StringUtils.firstNonBlank

@Service
class BilibiliApi {

    companion object {

        private const val API_CONNECT_TIMEOUT_MS = 8_000L
        private const val API_CALL_TIMEOUT_MS = 8_000L
        private const val RELATION_TIMEOUT_MS = 6_000L
        private const val SHORT_LINK_TIMEOUT_MS = 6_000L

        private const val VIEW_API_URL = "https://api.bilibili.com/x/web-interface/view"
        private const val RELATION_API_URL = "https://api.bilibili.com/x/relation/stat"
        private const val HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private val UA = "Mozilla/5.0 (compatible; ${Constants.UA}; +https://bilibili.com)"

        private val JSON_HEADERS = mapOf(
            "User-Agent" to UA,
            "Accept" to "application/json",
            "Accept-Encoding" to "identity",
        )

    }

    private val noRedirectClientCache = OkHttpUtils.ProxyAwareOkHttpClientCache(
        connectTimeoutMs = API_CONNECT_TIMEOUT_MS,
        callTimeoutMs = SHORT_LINK_TIMEOUT_MS,
        followRedirects = false,
        followSslRedirects = false,
    )

    private val redirectingClientCache = OkHttpUtils.ProxyAwareOkHttpClientCache(
        connectTimeoutMs = API_CONNECT_TIMEOUT_MS,
        callTimeoutMs = SHORT_LINK_TIMEOUT_MS,
        followRedirects = true,
        followSslRedirects = true,
    )

    private val noRedirectHttp: OkHttpClient
        get() = noRedirectClientCache.client

    private val redirectingHttp: OkHttpClient
        get() = redirectingClientCache.client

    fun resolveVideoId(input: String): VideoId? {
        VideoId.parse(input)?.let { return it }
        if (!input.startsWith("http", ignoreCase = true)) return null
        return resolveShortLink(input)
    }

    fun getViewData(id: VideoId): ObjectNode? {
        val root = HttpJson.getObject(
            uri = VIEW_API_URL,
            params = when (id) {
                is VideoId.Bv -> mapOf("bvid" to id.bvid)
                is VideoId.Av -> mapOf("aid" to id.aid.toString())
            },
            headers = JSON_HEADERS,
            readTimeoutMs = API_CALL_TIMEOUT_MS.toInt(),
        )
        if (root.int("code") != 0) return null
        return root.obj("data")
    }

    fun getFollowerCount(mid: Long): Long? = runCatching {
        val root = HttpJson.getObject(
            uri = RELATION_API_URL,
            params = mapOf("vmid" to mid.toString()),
            headers = JSON_HEADERS,
            readTimeoutMs = RELATION_TIMEOUT_MS.toInt(),
        )
        if (root.int("code") != 0) return@runCatching null
        root.obj("data")?.long("follower")
    }.getOrNull()

    private fun resolveShortLink(url: String): VideoId? {
        VideoId.parse(url)?.let { return it }

        resolveLocation(url)?.let { location ->
            VideoId.parse(location)?.let { return it }
            resolveFinalVideoId(location)?.let { return it }
        }

        return resolveFinalVideoId(url)
    }

    private fun resolveLocation(url: String): String? {
        noRedirectHttp.newCall(htmlRequest(url)).execute().use { response ->
            return firstNonBlank(
                response.header("Location"),
                response.header("location"),
                response.header("Content-Location"),
                response.header("content-location"),
            ).ifBlank { null }
        }
    }

    private fun resolveFinalVideoId(url: String): VideoId? {
        redirectingHttp.newCall(htmlRequest(url)).execute().use { response ->
            VideoId.parse(response.request.url.toString())?.let { return it }
            return VideoId.parse(response.body.string())
        }
    }

    private fun htmlRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", UA)
            .header("Accept", HTML_ACCEPT)
            .build()

}
