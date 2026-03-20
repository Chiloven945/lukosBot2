package top.chiloven.lukosbot2.commands.impl.kemono.schema

import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.str

data class Embed(
    val url: String,
    val subject: String?,
    val description: String?,
) {

    companion object {

        fun fromJsonObject(obj: JsonObject): Embed {
            return Embed(
                url = obj.str("url")!!,
                subject = obj.str("subject"),
                description = obj.str("description"),
            )
        }

    }

    fun getString(): String {
        return buildString {
            subject?.let { appendLine(it) }
            description?.let { appendLine(it) }
            appendLine(url)
        }.trim()
    }

}
