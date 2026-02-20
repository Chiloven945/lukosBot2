package top.chiloven.lukosbot2.commands.impl.e621.schema

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.bool
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.StringUtils.toText
import top.chiloven.lukosbot2.util.TimeUtils
import top.chiloven.lukosbot2.util.TimeUtils.toLDT
import java.time.LocalDateTime
import java.time.OffsetDateTime

data class Artist(
    val id: Int,
    val name: String,
    val updatedAt: LocalDateTime,
    val isActive: Boolean,
    val otherNames: List<String>?,
    val linkedUserId: String?,
    val createdAt: LocalDateTime,
    val creatorId: Int,
    val isLocked: Boolean,
    val notes: String?,
    val domains: List<Domain>?,
    val urls: List<Url>?
) {
    companion object {
        fun fromJsonArray(arr: JsonArray): List<Artist> =
            arr.map { fromJsonObject(it.asJsonObject) }

        fun fromJsonObject(obj: JsonObject): Artist = Artist(
            id = obj.int("id")!!,
            name = obj.str("name")!!,
            updatedAt = OffsetDateTime.parse(obj.str("updated_at")!!).toLDT(),
            isActive = obj.bool("is_active")!!,
            otherNames = obj.arr("other_names")
                ?.map { it.asString }
                ?.ifEmpty { null },
            linkedUserId = obj.str("linked_user_id"),
            createdAt = OffsetDateTime.parse(obj.str("created_at")!!).toLDT(),
            creatorId = obj.int("creator_id")!!,
            isLocked = obj.bool("is_locked")!!,
            notes = obj.str("notes"),
            domains = obj.arr("domains")
                ?.takeIf { !it.isEmpty }
                ?.let(Domain::fromJsonArray),
            urls = obj.arr("urls")
                ?.takeIf { !it.isEmpty }
                ?.let(Url::fromJsonArray)
        )
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
                    it.forEach { domain ->
                        appendLine(domain.getString())
                    }
                }

            urls
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    appendLine()
                    appendLine("链接：")
                    it.forEach { url ->
                        appendLine(url.getString())
                    }
                }
        }.toString().trimEnd()
    }

    fun getStringBrief(): String =
        "  - $name（$id）" + (otherNames?.take(3)?.joinToString("，", prefix = "：")?.let { "$it..." } ?: "")

    data class Domain(
        val domain: String,
        val count: Int
    ) {
        companion object {
            fun fromJsonArray(arr: JsonArray): List<Domain> =
                arr.map { fromSingleJsonArray(it.asJsonArray) }

            fun fromSingleJsonArray(arr: JsonArray): Domain = Domain(
                domain = arr.get(0).asString,
                count = arr.get(1).asInt
            )
        }

        fun getString(): String = "  - $domain：共 $count 个帖子"
    }

    data class Url(
        val id: Int,
        val artistId: Int,
        val url: String,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
        val isActive: Boolean
    ) {
        companion object {
            fun fromJsonArray(arr: JsonArray): List<Url> =
                arr.map { fromJsonObject(it.asJsonObject) }

            fun fromJsonObject(obj: JsonObject): Url = Url(
                id = obj.int("id")!!,
                artistId = obj.int("artist_id")!!,
                url = obj.str("url")!!,
                createdAt = OffsetDateTime.parse(obj.str("created_at")!!).toLDT(),
                updatedAt = OffsetDateTime.parse(obj.str("updated_at")!!).toLDT(),
                isActive = obj.bool("is_active")!!
            )
        }

        fun getString(): String =
            "  - ${if (!isActive) "（失效）" else ""}$url"
    }
}

