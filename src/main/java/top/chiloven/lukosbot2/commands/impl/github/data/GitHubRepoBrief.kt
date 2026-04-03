package top.chiloven.lukosbot2.commands.impl.github.data

import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils

data class GitHubRepoBrief(
    val fullName: String? = null,
    val htmlUrl: String? = null,
    val stargazersCount: Int = 0
) {

    fun toReadableLine(): String {
        val name = fullName ?: "(unknown)"
        return "$name - ${stargazersCount}★"
    }

    companion object {

        fun from(obj: ObjectNode): GitHubRepoBrief =
            JsonUtils.snakeTreeToValue(obj, GitHubRepoBrief::class.java)

    }

}
