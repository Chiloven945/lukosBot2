package top.chiloven.lukosbot2.commands.impl.kemono.schema

import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.JsonUtils.JsonLdt
import top.chiloven.lukosbot2.util.StringUtils
import top.chiloven.lukosbot2.util.TimeUtils.fmt
import java.time.LocalDateTime

data class HashSearchFile(
    val id: String = "",
    val hash: String = "",
    @JsonLdt
    val mtime: LocalDateTime = LocalDateTime.MIN,
    @JsonLdt
    val ctime: LocalDateTime = LocalDateTime.MIN,
    val mime: String = "",
    val ext: String = "",
    @JsonLdt
    val added: LocalDateTime = LocalDateTime.MIN,
    val size: Long = 0,
    val ihash: String? = null,
    val posts: List<PostSimple> = emptyList(),
) {

    companion object {

        fun fromJsonObject(obj: ObjectNode): HashSearchFile =
            JsonUtils.snakeTreeToValue(obj, HashSearchFile::class.java)

    }

    fun getString(): String {
        return buildString {
            appendLine("$hash$ext")
            appendLine("修改时间：${mtime.fmt()}")
            appendLine("创建时间：${ctime.fmt()}")
            appendLine("添加时间：${added.fmt()}")
            appendLine("大小：${StringUtils.fmtBytes(size)}")
            if (posts.isEmpty()) {
                appendLine("来自：无关联帖子")
            } else {
                appendLine("来自：")
                posts.forEach { post -> appendLine("  - ${post.getBrief()}") }
            }
        }.trim()
    }

}
