package top.chiloven.lukosbot2.commands.impl.kemono.schema

import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.JsonUtils.JsonLdt
import top.chiloven.lukosbot2.util.JsonUtils.isNotEmpty
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.StringUtils.truncate
import top.chiloven.lukosbot2.util.TimeUtils.fmtDate
import java.time.LocalDateTime

data class PostSimple(
    val id: String = "",
    val user: String = "",
    val service: Service = Service.UNKNOWN,
    val title: String = "",
    val substring: String = "",
    @JsonLdt
    val published: LocalDateTime = LocalDateTime.MIN,
    val file: Item? = null,
    val attachments: List<Item> = emptyList(),
) {

    companion object {

        fun fromSingleSimplePost(obj: ObjectNode): PostSimple =
            JsonUtils.snakeTreeToValue(normalize(obj), PostSimple::class.java)

        fun fromArraySimplePost(arr: ArrayNode): List<PostSimple> =
            arr.mapNotNull { it.asObjectOpt().orElse(null) }
                .map(::fromSingleSimplePost)

        private fun normalize(source: ObjectNode): ObjectNode {
            val node = source.deepCopy()
            if (node.obj("file")?.isNotEmpty() == false) {
                node.remove("file")
            }
            return node
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
