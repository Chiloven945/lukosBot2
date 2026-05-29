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

import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.JsonUtils.JsonLdt
import top.chiloven.lukosbot2.util.JsonUtils.isNotEmpty
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.StringUtils.truncate
import top.chiloven.lukosbot2.util.TimeUtils.fmtDate
import java.time.LocalDateTime

data class PostSimple(
    val id: String = "",
    val user: String = "",
    val service: Service = Service.UNKNOWN,
    val title: String = "",
    val substring: String = "",
    @JsonLdt
    val published: LocalDateTime = LocalDateTime.MIN,
    val file: Item? = null,
    val attachments: List<Item> = emptyList(),
) {

    companion object {

        fun fromSingleSimplePost(obj: ObjectNode): PostSimple =
            JsonUtils.snakeTreeToValue(normalize(obj), PostSimple::class.java)

        fun fromArraySimplePost(arr: ArrayNode): List<PostSimple> =
            arr.mapNotNull { it.asObjectOpt().orElse(null) }
                .map(::fromSingleSimplePost)

        private fun normalize(source: ObjectNode): ObjectNode {
            val node = source.deepCopy()
            if (node.obj("file")?.isNotEmpty() == false) {
                node.remove("file")
            }
            return node
        }

    }

    fun getBrief(): String {
        val header = buildString {
            append("$title ($id) - ${published.fmtDate()}")
            if (attachments.isNotEmpty()) append(" [${attachments.size} 附件]")
        }
        val sub = substring.trim().takeIf { it.isNotEmpty() }?.truncate(80)
        return if (sub == null) header else "$header: $sub"
    }

}
