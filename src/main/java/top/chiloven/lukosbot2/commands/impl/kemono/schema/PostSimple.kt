package top.chiloven.lukosbot2.commands.impl.kemono.schema

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.isNotEmpty
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.StringUtils.truncate
import top.chiloven.lukosbot2.util.TimeUtils.fmtDate
import top.chiloven.lukosbot2.util.TimeUtils.toLDT
import java.time.LocalDateTime

data class PostSimple(
    val id: String,
    val user: String,
    val service: Service,
    val title: String,
    val substring: String,
    val published: LocalDateTime,
    val file: Item?,
    val attachments: List<Item>,
) {

    companion object {

        fun fromSingleSimplePost(obj: JsonObject): PostSimple {
            return PostSimple(
                id = obj.str("id")!!,
                user = obj.str("user")!!,
                service = Service.getService(obj.str("service")!!),
                title = obj.str("title")!!,
                substring = obj.str("substring")!!,
                published = obj.str("published")!!.toLDT(),
                file = obj.obj("file")?.takeIf { it.isNotEmpty() }?.let { Item.fromJsonObject(it) },
                attachments = Item.fromJsonArray(obj.arr("attachments")!!)
            )
        }

        fun fromArraySimplePost(arr: JsonArray): List<PostSimple> {
            return arr.mapNotNull { el ->
                el.takeIf { it.isJsonObject }?.asJsonObject?.let(::fromSingleSimplePost)
            }
        }

    }

    fun getBrief(): String {
        val header = buildString {
            append("$title ($id) - ${published.fmtDate()}")
            if (attachments.isNotEmpty()) append(" [${attachments.size} 附件]")
        }
        val sub = substring.trim().takeIf { it.isNotEmpty() }?.truncate(80)
        return if (sub == null) header else "$header: $sub"
    }

}
