package top.chiloven.lukosbot2.commands.definition.parser

data class ArgvParseResult(
    val values: Map<String, Any?>,
    val positionals: List<String>,
    val unknownOptions: List<String> = emptyList()
) {

    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String): T = values[name] as T

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrNull(name: String): T? = values[name] as? T

}
