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
package top.chiloven.lukosbot2.core

import org.springframework.stereotype.Component
import top.chiloven.lukosbot2.core.model.message.media.*
import top.chiloven.lukosbot2.platform.PlatformFileLoader
import java.io.IOException

@Component
class MediaRefLoader(
    private val platformFileLoaders: List<PlatformFileLoader>,
    private val urlMediaLoader: IUrlMediaLoader
) {

    @Throws(IOException::class)
    suspend fun load(ref: MediaRef?): LoadedPlatformMedia {
        return when (ref) {
            is BytesRef -> LoadedPlatformMedia(
                ref.bytes(),
                ref.name(),
                ref.mime()
            )

            is UrlRef -> urlMediaLoader.load(ref)
            is PlatformFileRef -> loadPlatform(ref)
            null -> throw IOException("不支持的媒体类型，无法读取。")
        }
    }

    private suspend fun loadPlatform(ref: PlatformFileRef): LoadedPlatformMedia {
        return platformFileLoaders.firstOrNull { it.supports(ref.platform()) }
                ?.load(ref)
            ?: throw IOException("当前平台不支持读取该媒体。")
    }

}
