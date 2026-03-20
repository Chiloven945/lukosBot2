package top.chiloven.lukosbot2.commands.impl.kemono.schema

import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.long
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.StringUtils
import top.chiloven.lukosbot2.util.TimeUtils.fmt
import top.chiloven.lukosbot2.util.TimeUtils.toLDT
import java.time.LocalDateTime

data class HashSearchFile(
    val id: String,
    val hash: String,
    val mtime: LocalDateTime,
    val ctime: LocalDateTime,
    val mime: String,
    val ext: String,
    val added: LocalDateTime,
    val size: Long,
    val ihash: String?,
    val posts: List<PostSimple>,
) {

    companion object {

        fun fromJsonObject(obj: JsonObject): HashSearchFile {
            return HashSearchFile(
                id = obj.str("id")!!,
                hash = obj.str("hash")!!,
                mtime = obj.str("mtime")!!.toLDT(),
                ctime = obj.str("ctime")!!.toLDT(),
                mime = obj.str("mime")!!,
                ext = obj.str("ext")!!,
                added = obj.str("added")!!.toLDT(),
                size = obj.long("size")!!,
                ihash = obj.str("ihash"),
                posts = PostSimple.fromArraySimplePost(obj.arr("posts")!!)
            )
        }

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
                posts.forEach { post ->
                    appendLine("  - ${post.getBrief()}")
                }
            }
        }.trim()
    }

}
