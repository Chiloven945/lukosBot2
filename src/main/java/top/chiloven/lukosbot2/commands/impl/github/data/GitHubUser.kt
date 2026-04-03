package top.chiloven.lukosbot2.commands.impl.github.data

import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils

data class GitHubUser(
    val login: String = "",
    val name: String? = null,
    val htmlUrl: String? = null,
    val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0
) {

    fun toReadableText(): String {
        val displayName = name?.takeIf { it.isNotBlank() } ?: login
        val url = htmlUrl ?: "(无)"
        return """
            用户: $displayName ($login)
            主页: $url
            公开仓库: $publicRepos | 粉丝: $followers | 关注: $following
        """.trimIndent()
    }

    companion object {

        fun from(obj: ObjectNode): GitHubUser =
            JsonUtils.snakeTreeToValue(obj, GitHubUser::class.java)

    }

}
