package top.chiloven.lukosbot2.commands.definition

sealed interface SyntaxItem {

    data class Lit(
        val text: String
    ) : SyntaxItem

    data class Arg(
        val name: String
    ) : SyntaxItem

    data class Opt(
        val item: SyntaxItem
    ) : SyntaxItem

    data class Choice(
        val items: List<SyntaxItem>
    ) : SyntaxItem

    data class Group(
        val items: List<SyntaxItem>
    ) : SyntaxItem

    data class Concat(
        val items: List<SyntaxItem>
    ) : SyntaxItem

}

data class CommandSyntaxSpec(
    val description: String = "",
    val items: List<SyntaxItem> = emptyList()
)
