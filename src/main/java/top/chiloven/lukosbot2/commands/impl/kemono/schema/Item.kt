package top.chiloven.lukosbot2.commands.impl.kemono.schema

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.str
import java.net.URI

private const val KEMONO_BASE = "https://kemono.cr"

data class Item(
    val name: String,
    val path: URI,
) {

    companion object {

        fun fromJsonObject(obj: JsonObject): Item {
            return Item(
                name = obj.str("name")!!,
                path = URI.create(obj.str("path")!!)
            )
        }

        fun fromJsonArray(arr: JsonArray): List<Item> {
            return arr.mapNotNull { el ->
                el.takeIf { it.isJsonObject }?.asJsonObject?.let(::fromJsonObject)
            }
        }

        fun getStringInList(items: List<Item>): String {
            return items.joinToString("\n") { it.getString() }
        }

    }

    val resolvedUrl: String
        get() {
            val raw = path.toString().trim()
            if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
            return if (raw.startsWith('/')) KEMONO_BASE + raw else "$KEMONO_BASE/$raw"
        }

    fun getString(): String = "  - $name: $resolvedUrl"

}
