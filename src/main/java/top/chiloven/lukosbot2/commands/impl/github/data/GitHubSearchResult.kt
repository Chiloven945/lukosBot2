package top.chiloven.lukosbot2.commands.impl.github.data

import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils

data class GitHubSearchResult(
    val totalCount: Int = 0,
    val items: List<GitHubRepoBrief> = emptyList()
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

        fun from(obj: ObjectNode, top: Int): GitHubSearchResult {
            val mapped = JsonUtils.snakeTreeToValue(obj, GitHubSearchResult::class.java)
            return mapped.copy(items = mapped.items.take(top))
        }

    }

}
