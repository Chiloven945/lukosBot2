package top.chiloven.lukosbot2.commands.impl.github.data

import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.str

data class GitHubRepoBrief(
    val fullName: String?,
    val htmlUrl: String?,
    val stars: Int
) {

    fun toReadableLine(): String {
        val name = fullName ?: "(unknown)"
        return "$name - ${stars}★"
    }

    companion object {
        fun from(obj: JsonObject): GitHubRepoBrief = GitHubRepoBrief(
            fullName = obj.str("full_name"),
            htmlUrl = obj.str("html_url"),
            stars = obj.int("stargazers_count")!!,
        )
    }

}
