package top.chiloven.lukosbot2.commands.impl.kemono.schema

import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils
import top.chiloven.lukosbot2.util.JsonUtils.JsonLdt
import top.chiloven.lukosbot2.util.TimeUtils.fmt
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

        fun fromProfileAndPosts(obj: ObjectNode, arr: ArrayNode): Creator {
            val profile = JsonUtils.snakeTreeToValue(obj, Profile::class.java)
            return Creator(
                id = profile.id,
                name = profile.name,
                service = profile.service,
                indexed = profile.indexed,
                updated = profile.updated,
                publicId = profile.publicId,
                relationId = profile.relationId,
                postCount = profile.postCount,
                dmCount = profile.dmCount,
                shareCount = profile.shareCount,
                chatCount = profile.chatCount,
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

    private data class Profile(
        val id: String = "",
        val name: String = "",
        val service: Service = Service.UNKNOWN,
        @JsonLdt
        val indexed: LocalDateTime = LocalDateTime.MIN,
        @JsonLdt
        val updated: LocalDateTime = LocalDateTime.MIN,
        val publicId: String = "",
        val relationId: String? = null,
        val postCount: Int = 0,
        val dmCount: Int = 0,
        val shareCount: Int = 0,
        val chatCount: Int = 0,
    )

}
