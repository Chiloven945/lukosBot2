package top.chiloven.lukosbot2.commands.impl.kemono.schema

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

    fun getString(): String = "  - $name: $resolvedUrl"

}
