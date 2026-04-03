package top.chiloven.lukosbot2.commands.impl.e621.schema

import com.fasterxml.jackson.annotation.JsonCreator
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.JsonUtils.JsonLdt
import top.chiloven.lukosbot2.util.StringUtils.toText
import top.chiloven.lukosbot2.util.TimeUtils
import java.time.LocalDateTime

data class Artist(
    val id: Int = 0,
    val name: String = "",
    @JsonLdt
    val updatedAt: LocalDateTime = LocalDateTime.MIN,
    val isActive: Boolean = false,
    val otherNames: List<String>? = null,
    val linkedUserId: String? = null,
    @JsonLdt
    val createdAt: LocalDateTime = LocalDateTime.MIN,
    val creatorId: Int = 0,
    val isLocked: Boolean = false,
    val notes: String? = null,
    val domains: List<Domain>? = null,
    val urls: List<Url>? = null
) {

    companion object {

        fun fromJsonArray(arr: ArrayNode): List<Artist> =
            JsonUtils.snakeTreeToList(arr, Artist::class.java)

        fun fromJsonObject(obj: ObjectNode): Artist =
            JsonUtils.snakeTreeToValue(obj, Artist::class.java)

    }

    fun getString(): String {
        return StringBuilder().apply {
            appendLine("$name（$id）")
            appendLine("创建于：${createdAt.format(TimeUtils.dtf())}")
            appendLine("更新于：${updatedAt.format(TimeUtils.dtf())}")
            appendLine("是否活跃：${isActive.toText()}")

            otherNames
                ?.takeIf { it.isNotEmpty() }
                ?.let { appendLine("别名：${it.joinToString("，")}") }

            notes
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    appendLine()
                    appendLine(it)
                }

            domains
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    appendLine()
                    appendLine("平台：")
                    it.forEach { domain -> appendLine(domain.getString()) }
                }

            urls
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    appendLine()
                    appendLine("链接：")
                    it.forEach { url -> appendLine(url.getString()) }
                }
        }.toString().trimEnd()
    }

    fun getStringBrief(): String =
        "  - $name（$id）" + (otherNames?.take(3)?.joinToString("，", prefix = "：")?.let { "$it..." } ?: "")

    data class Domain(
        val domain: String,
        val count: Int
    ) {

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        constructor(values: List<Any?>) : this(
            domain = values.getOrNull(0)?.toString().orEmpty(),
            count = (values.getOrNull(1) as? Number)?.toInt() ?: 0
        )

        fun getString(): String = "  - $domain：共 $count 个帖子"

    }

    data class Url(
        val id: Int = 0,
        val artistId: Int = 0,
        val url: String = "",
        @JsonLdt
        val createdAt: LocalDateTime = LocalDateTime.MIN,
        @JsonLdt
        val updatedAt: LocalDateTime = LocalDateTime.MIN,
        val isActive: Boolean = false
    ) {

        fun getString(): String = "  - ${if (!isActive) "（失效）" else ""}$url"

    }

}
