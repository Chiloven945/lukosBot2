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
package top.chiloven.lukosbot2.commands.bot.music

import top.chiloven.lukosbot2.util.TimeUtils.fmtTime

data class TrackInfo(
    val platform: MusicPlatform,
    val id: String,
    val name: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val url: String? = null,
    val durationMs: Long = 0L
) {

    fun formatted(): String = buildString {
        append("平台：").append(platform.displayName).append('\n')
        append("标题：").append(name).append('\n')
        append("艺术家：").append(artist).append('\n')

        album?.takeIf { it.isNotBlank() }?.let {
            append("专辑：").append(it).append('\n')
        }
        if (durationMs > 0) {
            append("时长：").append(durationMs.fmtTime("mm:ss")).append('\n')
        }
        url?.takeIf { it.isNotBlank() }?.let {
            append("链接：").append(it).append('\n')
        }
        coverUrl?.takeIf { it.isNotBlank() }?.let {
            append("封面：").append(it).append('\n')
        }
    }

}
