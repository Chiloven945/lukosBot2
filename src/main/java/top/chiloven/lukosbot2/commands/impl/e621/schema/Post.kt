package top.chiloven.lukosbot2.commands.impl.e621.schema

import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.JsonUtils.JsonLdt
import top.chiloven.lukosbot2.util.JsonUtils.bool
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.StringUtils.appendLineIfNotEmpty
import top.chiloven.lukosbot2.util.StringUtils.appendSectionIfNotEmpty
import top.chiloven.lukosbot2.util.StringUtils.fmtBytes
import top.chiloven.lukosbot2.util.StringUtils.fmtTimeSec
import top.chiloven.lukosbot2.util.TimeUtils
import java.time.LocalDateTime
import kotlin.math.absoluteValue

data class Post(
    val id: Int = 0,
    @JsonLdt
    val createdAt: LocalDateTime = LocalDateTime.MIN,
    @JsonLdt
    val updatedAt: LocalDateTime = LocalDateTime.MIN,
    val file: File = File(),
    val preview: Preview = Preview(),
    val sample: Sample = Sample(),
    val score: Score = Score(),
    val tags: Tags = Tags(),
    val lockedTags: List<String>? = null,
    val changeSeq: Float = 0f,
    val flags: Flags = Flags(),
    val rating: String = "",
    val favCount: Int = 0,
    val sources: List<String> = emptyList(),
    val pools: List<Int> = emptyList(),
    val relationships: Relationships? = null,
    val approverId: Int? = null,
    val uploaderId: Int? = null,
    val description: String = "",
    val commentCount: Int = 0,
    val isFavorited: Boolean = false,
    val hasNotes: Boolean = false,
    val duration: Float? = null,
    val uploaderName: String = ""
) {

    companion object {

        fun fromJsonObject(obj: ObjectNode): Post = JsonUtils.snakeTreeToValue(normalize(obj), Post::class.java)

        fun fromJsonArray(arr: tools.jackson.databind.node.ArrayNode): List<Post> =
            arr.mapNotNull { it.asObjectOpt().orElse(null) }.map(::fromJsonObject)

        private fun normalize(source: ObjectNode): ObjectNode {
            val node = source.deepCopy()

            node.obj("relationships")?.let { rel ->
                val parentNull = !rel.has("parent_id") || rel.get("parent_id").isNull
                val hasChildren = rel.bool("has_children") == true
                if (parentNull && !hasChildren) {
                    node.remove("relationships")
                }
            }

            node.obj("sample")?.obj("alternates")?.let { alternates ->
                val hasAlternates = alternates.bool("has") == true
                if (alternates.isEmpty || !hasAlternates) {
                    node.obj("sample")?.remove("alternates")
                }
            }

            return node
        }

    }

    fun getString(): String =
        buildString {
            appendLine("帖子 $id")
            appendLine("发布于：${createdAt.format(TimeUtils.dtf())}")
            appendLine("更新于：${updatedAt.format(TimeUtils.dtf())}")
            appendLine("分级：${rating.uppercase()}")
            appendLine()
            appendLineIfNotEmpty(description, blankLineAfter = true)
            append(file.getString(duration))
            appendLine()
            append(tags.getString())
            appendLine()
            relationships?.let { append(it.getString()) }
            appendLine("${score.getString()} | ❤️$favCount | 💬$commentCount")
        }

    fun getStringBrief(): String = "  - $id：${tags.getStringArtist()}"

    data class File(
        val width: Int = 0,
        val height: Int = 0,
        val ext: String = "",
        val size: Long = 0,
        val md5: String = "",
        val url: String? = null
    ) {

        fun getString(duration: Float?): String = buildString {
            appendLine("文件：$md5.$ext")
            appendLine("分辨率：$width × $height")
            appendLine("大小：${fmtBytes(size)}")
            if (duration != null) appendLine("时长：${fmtTimeSec(duration)}")
            if (url != null) appendLine("链接：$url")
        }

    }

    data class Preview(
        val width: Int = 0,
        val height: Int = 0,
        val url: String? = null,
        val alt: String? = null
    )

    data class Sample(
        val has: Boolean = false,
        val width: Int? = null,
        val height: Int? = null,
        val url: String? = null,
        val alt: String? = null,
        val alternates: Alternates? = null
    ) {

        data class Alternates(
            val has: Boolean = false,
            val original: Alternate? = null,
            val variants: Variants? = null,
            val samples: Samples? = null
        ) {

            data class Alternate(
                val fps: Float = 0f,
                val codec: String = "",
                val size: Long = 0,
                val width: Int = 0,
                val height: Int = 0,
                val url: String = ""
            )

            data class Variants(
                val web: Alternate? = null,
                val mp4: Alternate? = null
            )

            data class Samples(
                @JsonProperty("480p")
                val p480: Alternate? = null,
                @JsonProperty("720p")
                val p720: Alternate? = null,
            )

        }

    }

    data class Score(
        val up: Int = 0,
        val down: Int = 0,
        val total: Int = 0,
    ) {

        fun getString(): String = "🗳️ $total（🔼 $up 🔽 ${down.absoluteValue}）"

    }

    data class Tags(
        val general: List<String> = emptyList(),
        val artist: List<String> = emptyList(),
        val copyright: List<String> = emptyList(),
        val character: List<String> = emptyList(),
        val species: List<String> = emptyList(),
        val invalid: List<String> = emptyList(),
        val meta: List<String> = emptyList(),
        val lore: List<String> = emptyList(),
        val contributor: List<String> = emptyList()
    ) {

        fun getString(): String = buildString {
            appendSectionIfNotEmpty("作者", artist, prefix = "  - ")
            appendSectionIfNotEmpty("版权", copyright, prefix = "  - ")
            appendSectionIfNotEmpty("角色", character, prefix = "  - ")
            appendLineIfNotEmpty("物种", species)
            appendLineIfNotEmpty("通用", general)
            appendLineIfNotEmpty("源数据", meta)
            appendLineIfNotEmpty("剧情", lore)
            appendLineIfNotEmpty("贡献者", contributor)
        }

        fun getStringArtist(): String = artist.joinToString(separator = "、")

    }

    data class Flags(
        val pending: Boolean = false,
        val flagged: Boolean = false,
        val noteLocked: Boolean = false,
        val statusLocked: Boolean = false,
        val ratingLocked: Boolean = false,
        val deleted: Boolean = false,
    )

    data class Relationships(
        val parentId: Int? = null,
        val hasChildren: Boolean = false,
        val hasActiveChildren: Boolean = false,
        val children: List<Int> = emptyList()
    ) {

        fun getString(): String = buildString {
            appendLineIfNotEmpty(parentId?.toString(), "父帖子")
            if (hasChildren) appendLine("子帖子：${children.joinToString("、")}")
        }

    }

}
