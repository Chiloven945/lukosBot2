package top.chiloven.lukosbot2.commands.impl.kemono.schema

import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.JsonUtils.JsonLdt
import top.chiloven.lukosbot2.util.JsonUtils.isNotEmpty
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.StringUtils.appendSeparator
import top.chiloven.lukosbot2.util.TimeUtils.fmt
import java.time.LocalDateTime

data class Post(
    val id: String = "",
    val user: String = "",
    val service: Service = Service.UNKNOWN,
    val title: String = "",
    val content: String = "",
    val embed: Embed? = null,
    val sharedFile: Boolean = false,
    @JsonLdt
    val added: LocalDateTime = LocalDateTime.MIN,
    @JsonLdt
    val published: LocalDateTime = LocalDateTime.MIN,
    @JsonLdt
    val edited: LocalDateTime = LocalDateTime.MIN,
    val file: Item? = null,
    val attachments: List<Item> = emptyList(),
    val next: String? = null,
    val previous: String? = null,
) {

    companion object {

        fun fromJsonObject(obj: ObjectNode): Post =
            JsonUtils.snakeTreeToValue(normalize(obj), Post::class.java)

        fun fromSpecificPost(obj: ObjectNode): Post = fromJsonObject(obj.obj("post")!!)

        private fun normalize(source: ObjectNode): ObjectNode {
            val node = source.deepCopy()
            if (node.obj("embed")?.isNotEmpty() == false) {
                node.remove("embed")
            }
            if (node.obj("file")?.isNotEmpty() == false) {
                node.remove("file")
            }
            return node
        }

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
