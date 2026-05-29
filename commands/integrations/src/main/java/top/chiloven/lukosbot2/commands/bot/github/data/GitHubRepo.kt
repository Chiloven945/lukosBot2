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

data class GitHubRepo(
    val fullName: String? = null,
    val htmlUrl: String? = null,
    val language: String? = null,
    val stargazersCount: Int = 0,
    val forksCount: Int = 0,
    val description: String? = null
) {

    fun toReadableText(): String {
        val name = fullName ?: "未知仓库"
        val url = htmlUrl ?: "无"
        val lang = language?.takeIf { it.isNotBlank() } ?: "未知"
        val desc = description?.takeIf { it.isNotBlank() } ?: "无"
        return """
            仓库：$name
            主页：$url
            语言：$lang | 收藏：$stargazersCount | 分叉：$forksCount
            描述：$desc
        """.trimIndent()
    }

    companion object {

        fun from(obj: ObjectNode): GitHubRepo =
            JsonUtils.snakeTreeToValue(obj, GitHubRepo::class.java)

    }

}
