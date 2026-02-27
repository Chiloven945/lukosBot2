package top.chiloven.lukosbot2.commands.impl.github.data

import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.str

data class GitHubUser(
    val login: String,
    val name: String?,
    val htmlUrl: String?,
    val publicRepos: Int,
    val followers: Int,
    val following: Int
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
        fun from(obj: JsonObject): GitHubUser = GitHubUser(
            login = obj.str("login").orEmpty(),
            name = obj.str("name"),
            htmlUrl = obj.str("html_url"),
            publicRepos = obj.int("public_repos")!!,
            followers = obj.int("followers")!!,
            following = obj.int("following")!!,
        )
    }

}
