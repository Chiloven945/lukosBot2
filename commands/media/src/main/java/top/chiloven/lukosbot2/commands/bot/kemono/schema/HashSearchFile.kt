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
package top.chiloven.lukosbot2.commands.bot.kemono.schema

import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.JsonUtils.JsonLdt
import top.chiloven.lukosbot2.util.StringUtils
import top.chiloven.lukosbot2.util.TimeUtils.fmt
import java.time.LocalDateTime

data class HashSearchFile(
    val id: String = "",
    val hash: String = "",
    @JsonLdt
    val mtime: LocalDateTime = LocalDateTime.MIN,
    @JsonLdt
    val ctime: LocalDateTime = LocalDateTime.MIN,
    val mime: String = "",
    val ext: String = "",
    @JsonLdt
    val added: LocalDateTime = LocalDateTime.MIN,
    val size: Long = 0,
    val ihash: String? = null,
    val posts: List<PostSimple> = emptyList(),
) {

    companion object {

        fun fromJsonObject(obj: ObjectNode): HashSearchFile =
            JsonUtils.snakeTreeToValue(obj, HashSearchFile::class.java)

    }

    fun getString(): String {
        return buildString {
            appendLine("$hash$ext")
            appendLine("修改时间：${mtime.fmt()}")
            appendLine("创建时间：${ctime.fmt()}")
            appendLine("添加时间：${added.fmt()}")
            appendLine("大小：${StringUtils.fmtBytes(size)}")
            if (posts.isEmpty()) {
                appendLine("关联帖子：无")
            } else {
                appendLine("关联帖子：")
                posts.forEach { post -> appendLine("  - ${post.getBrief()}") }
            }
        }.trim()
    }

}
