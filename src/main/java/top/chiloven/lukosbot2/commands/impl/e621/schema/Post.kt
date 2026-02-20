package top.chiloven.lukosbot2.commands.impl.e621.schema

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.bool
import top.chiloven.lukosbot2.util.JsonUtils.float
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.long
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.StringUtils.appendLineIfNotEmpty
import top.chiloven.lukosbot2.util.StringUtils.appendSectionIfNotEmpty
import top.chiloven.lukosbot2.util.StringUtils.fmtBytes
import top.chiloven.lukosbot2.util.StringUtils.fmtTimeSec
import top.chiloven.lukosbot2.util.TimeUtils.toLDT
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.math.absoluteValue

data class Post(
    val id: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val file: File,
    val preview: Preview,
    val sample: Sample,
    val score: Score,
    val tags: Tags,
    val lockedTags: List<String>?,
    val changeSeq: Float,
    val flags: Flags,
    val rating: String,
    val favCount: Int,
    val sources: List<String>,
    val pools: List<Int>,
    val relationships: Relationships?,
    val approverId: Int?,
    val uploaderId: Int?,
    val description: String,
    val commentCount: Int,
    val isFavorited: Boolean,
    val hasNotes: Boolean,
    val duration: Float?,
    val uploaderName: String
) {
    companion object {
        fun fromJsonObject(obj: JsonObject): Post = Post(
            id = obj.int("id")!!,
            createdAt = OffsetDateTime.parse(obj.str("created_at")!!).toLDT(),
            updatedAt = OffsetDateTime.parse(obj.str("updated_at")!!).toLDT(),
            file = File.fromJsonObject(obj.obj("file")!!),
            preview = Preview.fromJsonObject(obj.obj("preview")!!),
            sample = Sample.fromJsonObject(obj.obj("sample")!!),
            score = Score.fromJsonObject(obj.obj("score")!!),
            tags = Tags.fromJsonObject(obj.obj("tags")!!),
            lockedTags = obj.arr("locked_tags")?.map { it.asString }?.ifEmpty { null },
            changeSeq = obj.float("change_seq")!!,
            flags = Flags.fromJsonObject(obj.obj("flags")!!),
            rating = obj.str("rating")!!,
            favCount = obj.int("fav_count")!!,
            sources = obj.arr("sources")!!.map { it.asString },
            pools = obj.arr("pools")!!.map { it.asInt },
            relationships = obj.obj("relationships")
                ?.takeUnless { rel ->
                    val parentNull = rel.get("parent_id")?.isJsonNull != false
                    val hasChildren = rel.bool("has_children") == true
                    parentNull && !hasChildren
                }
                ?.let(Relationships::fromJsonObject),
            approverId = obj.int("approver_id"),
            uploaderId = obj.int("uploader_id"),
            description = obj.str("description")!!,
            commentCount = obj.int("comment_count")!!,
            isFavorited = obj.bool("is_favorited")!!,
            hasNotes = obj.bool("has_notes")!!,
            duration = obj.float("duration"),
            uploaderName = obj.str("uploader_name")!!
        )

        fun fromJsonArray(arr: JsonArray): List<Post> =
            arr.map { fromJsonObject(it.asJsonObject) }
    }

    fun getString(): String =
        buildString {
            appendLine("帖子 $id")
            appendLine("发布于：$createdAt")
            appendLine("更新于：$updatedAt")
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
        val width: Int,
        val height: Int,
        val ext: String,
        val size: Long,
        val md5: String,
        val url: String?
    ) {
        companion object {
            fun fromJsonObject(obj: JsonObject): File = File(
                width = obj.int("width")!!,
                height = obj.int("height")!!,
                ext = obj.str("ext")!!,
                size = obj.long("size")!!,
                md5 = obj.str("md5")!!,
                url = obj.str("url")
            )
        }

        fun getString(duration: Float?): String = buildString {
            appendLine("文件：$md5.$ext")
            appendLine("分辨率：$width * $height")
            appendLine("大小：${fmtBytes(size)}")
            if (duration != null) {
                appendLine("时长：${fmtTimeSec(duration)}")
            }
            if (url != null) {
                appendLine("链接：$url")
            }
        }
    }

    data class Preview(
        val width: Int,
        val height: Int,
        val url: String?,
        val alt: String?
    ) {
        companion object {
            fun fromJsonObject(obj: JsonObject): Preview = Preview(
                width = obj.int("width")!!,
                height = obj.int("height")!!,
                url = obj.str("url"),
                alt = obj.str("alt")
            )
        }
    }

    data class Sample(
        val has: Boolean,
        val width: Int?,
        val height: Int?,
        val url: String?,
        val alt: String?,
        val alternates: Alternates?
    ) {
        companion object {
            fun fromJsonObject(obj: JsonObject): Sample = Sample(
                has = obj.bool("has")!!,
                width = obj.int("width"),
                height = obj.int("height"),
                url = obj.str("url"),
                alt = obj.str("alt"),
                alternates = obj.obj("alternates")
                    ?.takeUnless { it.entrySet().isEmpty() }
                    ?.takeIf { it.bool("has") == true }
                    ?.let(Alternates::fromJsonObject)
            )
        }

        data class Alternates(
            val has: Boolean,
            val original: Alternate?,
            val variants: Variants?,
            val samples: Samples?
        ) {
            companion object {
                fun fromJsonObject(obj: JsonObject): Alternates = Alternates(
                    has = obj.bool("has")!!,
                    original = obj.obj("original")?.let(Alternate::fromJsonObject),
                    variants = obj.obj("variants")?.let(Variants::fromJsonObject),
                    samples = obj.obj("samples")?.let(Samples::fromJsonObject)
                )
            }

            data class Alternate(
                val fps: Float,
                val codec: String,
                val size: Long,
                val width: Int,
                val height: Int,
                val url: String
            ) {
                companion object {
                    fun fromJsonObject(obj: JsonObject): Alternate = Alternate(
                        fps = obj.float("fps")!!,
                        codec = obj.str("codec")!!,
                        size = obj.long("size")!!,
                        width = obj.int("width")!!,
                        height = obj.int("height")!!,
                        url = obj.str("url")!!
                    )
                }
            }

            data class Variants(
                val web: Alternate?,
                val mp4: Alternate?
            ) {
                companion object {
                    fun fromJsonObject(obj: JsonObject): Variants = Variants(
                        web = obj.obj("web")?.let(Alternate::fromJsonObject),
                        mp4 = obj.obj("mp4")?.let(Alternate::fromJsonObject)
                    )
                }
            }

            data class Samples(
                val p480: Alternate?,
                val p720: Alternate?
            ) {
                companion object {
                    fun fromJsonObject(obj: JsonObject): Samples = Samples(
                        p480 = obj.obj("480p")?.let(Alternate::fromJsonObject),
                        p720 = obj.obj("720p")?.let(Alternate::fromJsonObject),
                    )
                }
            }

        }
    }

    data class Score(
        val up: Int,
        val down: Int,
        val total: Int,
    ) {
        companion object {
            fun fromJsonObject(obj: JsonObject): Score = Score(
                up = obj.int("up")!!,
                down = obj.int("down")!!,
                total = obj.int("total")!!,
            )
        }

        fun getString(): String = "🗳️ $total（🔼 $up 🔽 ${down.absoluteValue}）"
    }

    data class Tags(
        val general: List<String>,
        val artist: List<String>,
        val copyright: List<String>,
        val character: List<String>,
        val species: List<String>,
        val invalid: List<String>,
        val meta: List<String>,
        val lore: List<String>,
        val contributor: List<String>
    ) {
        companion object {
            fun fromJsonObject(obj: JsonObject): Tags = Tags(
                general = obj.arr("general")!!.map { it.asString },
                artist = obj.arr("artist")!!.map { it.asString },
                copyright = obj.arr("copyright")!!.map { it.asString },
                character = obj.arr("character")!!.map { it.asString },
                species = obj.arr("species")!!.map { it.asString },
                invalid = obj.arr("invalid")!!.map { it.asString },
                meta = obj.arr("meta")!!.map { it.asString },
                lore = obj.arr("lore")!!.map { it.asString },
                contributor = obj.arr("contributor")!!.map { it.asString }
            )
        }

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
        val pending: Boolean,
        val flagged: Boolean,
        val noteLocked: Boolean,
        val statusLocked: Boolean,
        val ratingLocked: Boolean,
        val deleted: Boolean,
    ) {
        companion object {
            fun fromJsonObject(obj: JsonObject): Flags = Flags(
                pending = obj.bool("pending")!!,
                flagged = obj.bool("flagged")!!,
                noteLocked = obj.bool("note_locked")!!,
                statusLocked = obj.bool("status_locked")!!,
                ratingLocked = obj.bool("rating_locked")!!,
                deleted = obj.bool("deleted")!!,
            )
        }
    }

    data class Relationships(
        val parentId: Int?,
        val hasChildren: Boolean,
        val hasActiveChildren: Boolean,
        val children: List<Int>
    ) {
        companion object {
            fun fromJsonObject(obj: JsonObject): Relationships = Relationships(
                parentId = obj.int("parent_id"),
                hasChildren = obj.bool("has_children")!!,
                hasActiveChildren = obj.bool("has_active_children")!!,
                children = obj.arr("children")!!.map { it.asInt },
            )
        }

        fun getString(): String = buildString {
            appendLineIfNotEmpty(parentId?.toString(), "父帖子")
            if (hasChildren) {
                appendLine("子帖子：${children.joinToString("、")}")
            }
        }
    }
}
