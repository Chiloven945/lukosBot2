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
package top.chiloven.lukosbot2.platform.telegram

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.core.model.message.media.LoadedPlatformMedia
import top.chiloven.lukosbot2.core.model.message.media.PlatformFileRef
import top.chiloven.lukosbot2.http.readBytePayload
import top.chiloven.lukosbot2.http.requireSuccess
import top.chiloven.lukosbot2.platform.PlatformFileLoader
import top.chiloven.lukosbot2.util.JsonUtils
import java.io.IOException

class TelegramFileLoader(
    private val appProperties: AppProperties,
    private val http: HttpClient
) : PlatformFileLoader {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TelegramGetFileResponse(
        val ok: Boolean = false,
        val result: TelegramFileDto? = null,
        val description: String? = null,
    ) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class TelegramFileDto(
            val fileId: String? = null,
            val filePath: String? = null,
            val fileSize: Long? = null,
        )

    }

    override fun supports(platform: String): Boolean = platform.equals(
        "telegram",
        ignoreCase = true
    )

    @Throws(IOException::class)
    override suspend fun load(ref: PlatformFileRef): LoadedPlatformMedia {
        val token = appProperties.telegram.botToken.trim()
        if (token.isBlank()) {
            throw IOException("Telegram 配置不完整，无法读取图片。")
        }

        val getFileResponse = http.get("https://api.telegram.org/bot$token/getFile") {
            parameter("file_id", ref.fileId())
        }.requireSuccess()

        val text = getFileResponse.bodyAsText()
        val getFileResult = JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            TelegramGetFileResponse::class.java
        )
        val filePath = getFileResult.result?.filePath?.takeIf { it.isNotBlank() }
            ?: throw IOException("Telegram 未返回文件路径：${getFileResult.description ?: "未知错误"}")

        val payload = http.get("https://api.telegram.org/file/bot$token/$filePath")
                .readBytePayload()
        return LoadedPlatformMedia(
            payload.bytes,
            payload.fileName ?: filePath.substringAfterLast('/').ifBlank { null },
            payload.mime
        )
    }

}
