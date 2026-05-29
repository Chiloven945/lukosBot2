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

data class Embed(
    val url: String = "",
    val subject: String? = null,
    val description: String? = null,
) {

    companion object {

        fun fromJsonObject(obj: ObjectNode): Embed = JsonUtils.snakeTreeToValue(obj, Embed::class.java)

    }

    fun getString(): String {
        return buildString {
            subject?.let { appendLine(it) }
            description?.let { appendLine(it) }
            appendLine(url)
        }.trim()
    }

}
