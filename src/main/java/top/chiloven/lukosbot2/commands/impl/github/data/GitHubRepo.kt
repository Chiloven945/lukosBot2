package top.chiloven.lukosbot2.commands.impl.github.data

import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils

data class GitHubRepo(
    val fullName: String? = null,
    val htmlUrl: String? = null,
    val language: String? = null,
    val stargazersCount: Int = 0,
    val forksCount: Int = 0,
    val description: String? = null
) {

    fun toReadableText(): String {
        val name = fullName ?: "(未知仓库)"
        val url = htmlUrl ?: "(无)"
        val lang = language?.takeIf { it.isNotBlank() } ?: "未知"
        val desc = description?.takeIf { it.isNotBlank() } ?: "无"
        return """
            仓库: $name
            主页: $url
            语言: $lang | Star: $stargazersCount | Fork: $forksCount
            描述: $desc
        """.trimIndent()
    }

    companion object {

        fun from(obj: ObjectNode): GitHubRepo =
            JsonUtils.snakeTreeToValue(obj, GitHubRepo::class.java)

    }

}
