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
import java.net.URI

data class Item(
    val name: String = "",
    val path: URI = URI.create("")
) {

    companion object {

        private const val KEMONO_BASE = "https://kemono.cr"

        fun fromJsonObject(obj: ObjectNode): Item =
            JsonUtils.snakeTreeToValue(obj, Item::class.java)

        fun fromJsonArray(arr: ArrayNode): List<Item> =
            JsonUtils.snakeTreeToList(arr, Item::class.java)

        fun getStringInList(items: List<Item>): String =
            items.joinToString("\n") { it.getString() }

    }

    val resolvedUrl: String
        get() {
            val raw = path.toString().trim()
            if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
            return if (raw.startsWith('/')) KEMONO_BASE + raw else "$KEMONO_BASE/$raw"
        }

    fun getString(): String = "  - $name：$resolvedUrl"

}
