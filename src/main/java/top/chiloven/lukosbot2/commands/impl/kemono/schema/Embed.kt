package top.chiloven.lukosbot2.commands.impl.kemono.schema

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
