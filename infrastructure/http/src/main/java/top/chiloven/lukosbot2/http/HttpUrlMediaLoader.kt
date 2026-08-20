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
package top.chiloven.lukosbot2.http

import io.ktor.client.*
import io.ktor.client.request.*
import org.springframework.stereotype.Component
import top.chiloven.lukosbot2.core.IUrlMediaLoader
import top.chiloven.lukosbot2.core.model.message.media.LoadedPlatformMedia
import top.chiloven.lukosbot2.core.model.message.media.UrlRef
import java.io.IOException

@Component
class HttpUrlMediaLoader(
    private val http: HttpClient
) : IUrlMediaLoader {

    @Throws(IOException::class)
    override suspend fun load(ref: UrlRef): LoadedPlatformMedia {
        val payload = http.get(ref.url()).readBytePayload()
        return LoadedPlatformMedia(
            payload.bytes,
            payload.fileName,
            payload.mime
        )
    }

}
