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

data class GitHubRepoBrief(
    val fullName: String? = null,
    val htmlUrl: String? = null,
    val stargazersCount: Int = 0
) {

    fun toReadableLine(): String {
        val name = fullName ?: "未知仓库"
        return "$name - ${stargazersCount}★"
    }

    companion object {

        fun from(obj: ObjectNode): GitHubRepoBrief =
            JsonUtils.snakeTreeToValue(obj, GitHubRepoBrief::class.java)

    }

}
