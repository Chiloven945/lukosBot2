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

data class GitHubUser(
    val login: String = "",
    val name: String? = null,
    val htmlUrl: String? = null,
    val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0
) {

    fun toReadableText(): String {
        val displayName = name?.takeIf { it.isNotBlank() } ?: login
        val url = htmlUrl ?: "无"
        return """
            用户：$displayName（$login）
            主页：$url
            公开仓库：$publicRepos | 粉丝：$followers | 关注：$following
        """.trimIndent()
    }

    companion object {

        fun from(obj: ObjectNode): GitHubUser =
            JsonUtils.snakeTreeToValue(obj, GitHubUser::class.java)

    }

}
