package top.chiloven.lukosbot2.core.command.definition

import top.chiloven.lukosbot2.core.command.definition.leaf.CommandLeaf
import top.chiloven.lukosbot2.core.command.definition.meta.CommandOptionDoc
import top.chiloven.lukosbot2.core.command.definition.meta.CommandParamDoc

/**
 * A single node in the recursive literal tree of a command definition.
 *
 * Each node represents one literal label in the command path (e.g. "user",
 * "search", "add"). Nodes can have:
 *
 *   - **Children** — nested literal subcommands
 *   - **A leaf** — the execution target for this path position
 *
 *
 * A node may have both children and a leaf simultaneously. For example,
 * `/cave` can execute as-is (random recall) while `/cave 12`
 * dispatches to an [top.chiloven.lukosbot2.core.command.definition.leaf.ArgvLeaf] and `/cave add` matches a child literal.
 *
 * ### Documentation fields
 * The `syntaxes`, `params`, `options`, `examples`,
 * and `notes` fields are consumed solely by `CommandUsageMapper`
 * to produce a `UsageNode` for help rendering. They do not affect
 * command execution.
 *
 * @param S the source type (propagated from [CommandDefinition])
 * @param name literal name matched case-insensitively at runtime
 * @param description one-line description for the usage tree
 * @param aliases alternative labels for this specific literal node
 * @param syntaxes hand-written usage syntax lines
 * @param params documented parameters (usage only)
 * @param options documented options/flags (usage only)
 * @param examples concrete invocation examples
 * @param notes free-form hints or caveats
 * @param children nested literal subcommands
 * @param leaf the execution strategy when this path is reached with no further child match
 */
data class CommandNode<S>(
    val name: String,
    val description: String = "",
    val aliases: List<String> = emptyList(),
    val syntaxes: List<CommandSyntax> = emptyList(),
    val params: List<CommandParamDoc> = emptyList(),
    val options: List<CommandOptionDoc> = emptyList(),
    val examples: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val children: List<CommandNode<S>> = emptyList(),
    val leaf: CommandLeaf<S>? = null
)
