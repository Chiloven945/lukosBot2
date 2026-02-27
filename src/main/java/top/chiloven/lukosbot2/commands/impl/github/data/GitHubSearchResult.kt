package top.chiloven.lukosbot2.commands.impl.github.data

import com.google.gson.JsonObject
import top.chiloven.lukosbot2.util.JsonUtils.int

data class GitHubSearchResult(
    val totalCount: Int,
    val items: List<GitHubRepoBrief>
) {

    fun toReadableText(): String {
        if (items.isEmpty()) return "未搜索到任何仓库。"
        val lines = buildString {
            append("【仓库搜索结果】(共 $totalCount)\n")
            for (r in items) {
                append(r.toReadableLine()).append('\n')
                r.htmlUrl?.let { append(it).append('\n') }
                append('\n')
            }
        }
        return lines.trimEnd()
    }

    companion object {
        fun from(obj: JsonObject, top: Int): GitHubSearchResult {
            val itemsArr = obj.getAsJsonArray("items")
            val repos = buildList {
                if (itemsArr != null && !itemsArr.isEmpty) {
                    val count = minOf(itemsArr.size(), top)
                    for (i in 0 until count) {
                        val repoObj = itemsArr[i].asJsonObject
                        add(GitHubRepoBrief.from(repoObj))
                    }
                }
            }
            return GitHubSearchResult(
                totalCount = obj.int("total_count")!!,
                items = repos
            )
        }
    }

}
