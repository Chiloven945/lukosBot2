package top.chiloven.lukosbot2.commands.impl.github.data

data class SearchParams(
    val keywords: String,
    val top: Int = 3,
    val language: String? = null,
    val sort: String? = null,
    val order: String? = null
) {

    companion object {
        fun parse(input: String): SearchParams {
            val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

            val opts: Map<String, String> = tokens.asSequence()
                .filter { it.startsWith("--") }
                .mapNotNull { t ->
                    val eq = t.indexOf('=')
                    if (eq > 2) t.substring(2, eq) to t.substring(eq + 1) else null
                }
                .toMap()

            val keywords = tokens.asSequence()
                .filter { t -> !t.startsWith("--") || t.indexOf('=') <= 2 }
                .joinToString(" ")
                .ifBlank { "java" }

            val top = opts["top"]?.toIntOrNull()?.coerceIn(1, 10) ?: 3

            return SearchParams(
                keywords = keywords,
                top = top,
                language = opts["lang"],
                sort = opts["sort"],
                order = opts["order"]
            )
        }
    }

}
