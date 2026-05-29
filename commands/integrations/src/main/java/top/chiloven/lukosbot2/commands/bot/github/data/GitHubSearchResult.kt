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
package top.chiloven.lukosbot2.commands.bot.github.data

import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils

data class GitHubSearchResult(
    val totalCount: Int = 0,
    val items: List<GitHubRepoBrief> = emptyList()
) {

    fun toReadableText(): String {
        if (items.isEmpty()) return "未搜索到任何仓库。"
        val lines = buildString {
            append("仓库搜索结果（共 $totalCount 个）：\n")
            for (r in items) {
                append(r.toReadableLine()).append('\n')
                r.htmlUrl?.let { append(it).append('\n') }
                append('\n')
            }
        }
        return lines.trimEnd()
    }

    companion object {

        fun from(obj: ObjectNode, top: Int): GitHubSearchResult {
            val mapped = JsonUtils.snakeTreeToValue(obj, GitHubSearchResult::class.java)
            return mapped.copy(items = mapped.items.take(top))
        }

    }

}
