package top.chiloven.lukosbot2.commands.impl.kemono.schema

import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.bool
import top.chiloven.lukosbot2.util.JsonUtils.isNotEmpty
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.StringUtils.appendSeparator
import top.chiloven.lukosbot2.util.TimeUtils.fmt
import top.chiloven.lukosbot2.util.TimeUtils.toLDT
import java.time.LocalDateTime

data class Post(
    val id: String,
    val user: String,
    val service: Service,
    val title: String,
    val content: String,
    val embed: Embed?,
    val sharedFile: Boolean,
    val added: LocalDateTime,
    val published: LocalDateTime,
    val edited: LocalDateTime,
    val file: Item?,
    val attachments: List<Item>,
    val next: String?,
    val previous: String?,
) {

    companion object {

        fun fromJsonObject(obj: JsonObject): Post {
            return Post(
                id = obj.str("id")!!,
                user = obj.str("user")!!,
                service = Service.getService(obj.str("service")!!),
                title = obj.str("title")!!,
                content = obj.str("content")!!,
                embed = obj.obj("embed")?.takeIf { it.isNotEmpty() }?.let { Embed.fromJsonObject(it) },
                sharedFile = obj.bool("shared_file")!!,
                added = obj.str("added")!!.toLDT(),
                published = obj.str("published")!!.toLDT(),
                edited = obj.str("edited")!!.toLDT(),
                file = obj.obj("file")?.takeIf { it.isNotEmpty() }?.let { Item.fromJsonObject(it) },
                attachments = Item.fromJsonArray(obj.arr("attachments")!!),
                next = obj.str("next"),
                previous = obj.str("previous"),
            )
        }

        fun fromSpecificPost(obj: JsonObject): Post = fromJsonObject(obj.obj("post")!!)

    }

    fun getSpecific(showAllAttachments: Boolean = false): String {
        return buildString {
            appendLine("[${service.siteName}/$user/$id] $title")
            appendLine("链接：https://kemono.cr/${service.id}/user/$user/post/$id")
            appendLine("添加时间：${added.fmt()}")
            appendLine("发布时间：${published.fmt()}")
            appendLine("编辑时间：${edited.fmt()}")
            if (content.isNotEmpty()) {
                appendSeparator()
                appendLine(content)
            }
            embed?.let {
                appendSeparator()
                append(it.getString())
            }
            appendSeparator()
            appendLine("共 ${attachments.size} 个附件")
            if (attachments.isEmpty()) {
                appendLine("此帖子没有任何附件。")
            } else {
                val display = if (showAllAttachments) attachments else attachments.take(10)
                display.forEach { item -> appendLine(item.getString()) }
                if (!showAllAttachments && attachments.size > 10) {
                    appendLine("当前已展示 10 个附件。追加 `-t` 可展示全部附件，追加 `-a` 可直接打包下载。")
                }
            }

            if (listOfNotNull(
                    previous?.let { "⬅️ $it" },
                    next?.let { "$it ➡️" }
                ).joinToString(" | ").isNotEmpty()
            ) {
                appendSeparator()
                appendLine(
                    listOfNotNull(
                        previous?.let { "⬅️ $it" },
                        next?.let { "$it ➡️" }
                    ).joinToString(" | "))
            }
        }.trim()
    }

    fun getAttachments(): String {
        return buildString {
            appendLine("[${service.siteName}/$user/$id] 此帖子共有 ${attachments.size} 个附件：")
            if (attachments.isEmpty()) {
                appendLine("此帖子没有任何附件。")
            } else {
                attachments.forEach { item -> appendLine(item.getString()) }
            }
        }.trim()
    }

    fun getBrief(): String = "$title ($id) - $user"

}
