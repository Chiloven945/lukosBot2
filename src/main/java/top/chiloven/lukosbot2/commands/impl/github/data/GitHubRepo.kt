package top.chiloven.lukosbot2.commands.impl.github.data

import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.str

data class GitHubRepo(
    val fullName: String?,
    val htmlUrl: String?,
    val language: String?,
    val stars: Int,
    val forks: Int,
    val description: String?
) {

    fun toReadableText(): String {
        val name = fullName ?: "(未知仓库)"
        val url = htmlUrl ?: "(无)"
        val lang = language?.takeIf { it.isNotBlank() } ?: "未知"
        val desc = description?.takeIf { it.isNotBlank() } ?: "无"
        return """
            仓库: $name
            主页: $url
            语言: $lang | Star: $stars | Fork: $forks
            描述: $desc
        """.trimIndent()
    }

    companion object {
        fun from(obj: JsonObject): GitHubRepo = GitHubRepo(
            fullName = obj.str("full_name"),
            htmlUrl = obj.str("html_url"),
            language = obj.str("language"),
            stars = obj.int("stargazers_count")!!,
            forks = obj.int("forks_count")!!,
            description = obj.str("description"),
        )
    }

}
