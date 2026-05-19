package top.chiloven.lukosbot2.core.command.definition

/**
 * Sealed hierarchy of renderable syntax items for [CommandSyntax].
 *
 * Mirrors the structure of `UsageNode.Item` to allow direct mapping
 * from DSL syntax declarations to the usage tree.
 */
sealed interface SyntaxItem {

    /** A literal token rendered as-is (e.g. `"--verbose"`). */
    data class Lit(
        val text: String
    ) : SyntaxItem

    /** A parameter placeholder rendered as `<name>`. */
    data class Arg(
        val name: String
    ) : SyntaxItem

    /** Wraps an item as optional, rendered as `[item]`. */
    data class Opt(
        val item: SyntaxItem
    ) : SyntaxItem

    /** A required choice, rendered as `(a|b)`. Requires at least 2 items. */
    data class Choice(
        val items: List<SyntaxItem>
    ) : SyntaxItem

    /** A space-separated group of items. Useful inside an [Opt] or [Choice]. */
    data class Group(
        val items: List<SyntaxItem>
    ) : SyntaxItem

    /** Concatenates items without spaces. Useful for `--key=<v>` forms. */
    data class Concat(
        val items: List<SyntaxItem>
    ) : SyntaxItem

}
