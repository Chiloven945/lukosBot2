package top.chiloven.lukosbot2.commands.impl.kemono.schema

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.TimeUtils.fmt
import top.chiloven.lukosbot2.util.TimeUtils.toLDT
import java.time.LocalDateTime

data class Creator(
    val id: String,
    val name: String,
    val service: Service,
    val indexed: LocalDateTime,
    val updated: LocalDateTime,
    val publicId: String,
    val relationId: String?,
    val postCount: Int,
    val dmCount: Int,
    val shareCount: Int,
    val chatCount: Int,
    val posts: List<PostSimple>,
) {

    companion object {

        fun fromProfileAndPosts(obj: JsonObject, arr: JsonArray): Creator {
            return Creator(
                id = obj.str("id")!!,
                name = obj.str("name")!!,
                service = Service.getService(obj.str("service")!!),
                indexed = obj.str("indexed")!!.toLDT(),
                updated = obj.str("updated")!!.toLDT(),
                publicId = obj.str("public_id")!!,
                relationId = obj.str("relation_id"),
                postCount = obj.int("post_count")!!,
                dmCount = obj.int("dm_count")!!,
                shareCount = obj.int("share_count")!!,
                chatCount = obj.int("chat_count")!!,
                posts = PostSimple.fromArraySimplePost(arr)
            )
        }

    }

    fun getString(postPreviewLimit: Int = 10): String {
        return buildString {
            appendLine("$name ($publicId) [${service.id}/$id]")
            appendLine("收录于：${indexed.fmt()}")
            appendLine("更新于：${updated.fmt()}")
            appendLine("📄 $postCount | 🔒 $dmCount | ↩️ $shareCount | 💬 $chatCount")

            if (posts.isNotEmpty()) {
                appendLine()
                appendLine("最近帖子：")
                posts.take(postPreviewLimit).forEach { post ->
                    appendLine("  - ${post.getBrief().replace("\n", "\n    ")}")
                }
                if (posts.size > postPreviewLimit) {
                    appendLine("当前仅展示最近 $postPreviewLimit 条；使用 `-a` 可打包下载该创作者全部帖子的附件。")
                }
            }
        }.trim()
    }

}
