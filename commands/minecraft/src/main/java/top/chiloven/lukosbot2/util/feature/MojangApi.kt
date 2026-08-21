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
package top.chiloven.lukosbot2.util.feature

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import top.chiloven.lukosbot2.http.requireSuccess
import top.chiloven.lukosbot2.util.Base64Utils
import top.chiloven.lukosbot2.util.JsonUtils
import java.io.IOException

class MojangApi(
    private val http: HttpClient
) {

    private val b64: Base64Utils = Base64Utils()

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class MojangProfileResponse(
        val id: String? = null,
        val name: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class MojangSessionProfileResponse(
        val id: String? = null,
        val name: String? = null,
        val properties: List<ProfilePropertyDto> = emptyList()
    ) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ProfilePropertyDto(
            val name: String? = null,
            val value: String? = null
        )

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class MojangTexturePayload(
        val textures: TexturesDto? = null
    ) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class TexturesDto(
            @JsonProperty("SKIN")
            val skin: TextureUrlDto? = null,
            @JsonProperty("CAPE")
            val cape: TextureUrlDto? = null
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class TextureUrlDto(
            val url: String? = null
        )

    }

    /**
     * Get UUID from player's name.
     * Returns null if the player is not found or there's an error.
     */
    @Throws(IOException::class)
    suspend fun getUuidFromName(name: String): String? = runCatching {
        val text = http.get("https://api.mojang.com/users/profiles/minecraft/$name")
                .requireSuccess()
                .bodyAsText()
        val res = JsonUtils.MAPPER.readValue(
            text,
            MojangProfileResponse::class.java
        )
        res.id
    }.getOrNull()

    /**
     * Get player's name from UUID.
     * Returns null if the player is not found or there's an error.
     */
    @Throws(IOException::class)
    suspend fun getNameFromUuid(uuid: String): String? = runCatching {
        val text = http.get("https://api.minecraftservices.com/minecraft/profile/lookup/$uuid")
                .requireSuccess()
                .bodyAsText()
        val res = JsonUtils.MAPPER.readValue(
            text,
            MojangProfileResponse::class.java
        )
        res.name
    }.getOrNull()

    /**
     * Get the full player information (skin, cape, etc.) using either UUID or player name.
     * Throws RuntimeException if an error occurs while fetching or parsing the data.
     */
    @Throws(IOException::class)
    suspend fun getMcPlayerInfo(data: String): McPlayer {
        val uuid = if (data.length <= 16) getUuidFromName(data) ?: data else data
        val text = http.get("https://sessionserver.mojang.com/session/minecraft/profile/$uuid")
                .requireSuccess()
                .bodyAsText()
        val sessionProfile = JsonUtils.MAPPER.readValue(
            text,
            MojangSessionProfileResponse::class.java
        )

        val rawProperty = sessionProfile.properties.firstOrNull()?.value.orEmpty()
        val texturePayload = runCatching {
            val json = b64.decodeToString(rawProperty)
            JsonUtils.MAPPER.readValue(
                json,
                MojangTexturePayload::class.java
            )
        }.getOrNull()

        return McPlayer(
            name = sessionProfile.name.orEmpty(),
            uuid = sessionProfile.id.orEmpty(),
            skin = texturePayload?.textures?.skin?.url,
            cape = texturePayload?.textures?.cape?.url
        )
    }

    @JvmRecord
    data class McPlayer(
        val name: String?,
        val uuid: String?,
        val skin: String?,
        val cape: String?
    ) {

        override fun toString(): String = buildString {
            appendLine("玩家名：$name")
            appendLine("UUID：$uuid")
            skin?.let { appendLine("皮肤：$it") }
            cape?.let { appendLine("披风：$it") }
        }

    }

}
