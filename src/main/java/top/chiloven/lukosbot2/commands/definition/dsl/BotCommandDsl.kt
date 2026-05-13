package top.chiloven.lukosbot2.commands.definition.dsl

import top.chiloven.lukosbot2.commands.definition.*
import top.chiloven.lukosbot2.commands.definition.parser.ArgvParseResult
import top.chiloven.lukosbot2.commands.definition.SyntaxItem as SpecSyntaxItem

@DslMarker
annotation class CommandSpecDsl

@CommandSpecDsl
class BotCommandBuilder(private val name: String) {

    var description: String = ""
    var visible: Boolean = true

    private val aliases = mutableListOf<String>()
    private val children = mutableListOf<CommandNodeSpec>()
    private val syntaxes = mutableListOf<CommandSyntaxSpec>()
    private val paramDocs = mutableListOf<CommandParamDoc>()
    private val optionDocs = mutableListOf<CommandOptionDoc>()
    private val exampleList = mutableListOf<String>()
    private val noteList = mutableListOf<String>()
    private var leaf: CommandLeafSpec? = null

    fun alias(vararg values: String) {
        aliases += values
    }

    fun literal(childName: String, block: NodeBuilder.() -> Unit) {
        children += NodeBuilder(childName).apply(block).build()
    }

    fun execute(block: CommandInvocation.() -> Unit) {
        leaf = EmptyLeafSpec { inv ->
            inv.block()
            1
        }
    }

    fun raw(
        argName: String = "text",
        required: Boolean = true,
        block: CommandInvocation.(String) -> Unit
    ) {
        leaf = RawLeafSpec(
            name = argName,
            required = required,
            executor = { inv ->
                inv.block(inv.rawTail)
                1
            }
        )
    }

    fun argv(block: ArgvBuilder.() -> Unit) {
        val builder = ArgvBuilder().apply(block)
        leaf = builder.buildLeaf()
    }

    fun syntax(description: String = "", vararg items: SpecSyntaxItem) {
        syntaxes += CommandSyntaxSpec(description, items.toList())
    }

    fun param(name: String, description: String = "") {
        paramDocs += CommandParamDoc(name, description)
    }

    fun optionDoc(name: String, description: String = "") {
        optionDocs += CommandOptionDoc(name, description)
    }

    fun example(vararg values: String) {
        exampleList += values
    }

    fun note(vararg values: String) {
        noteList += values
    }

    fun build(): BotCommandSpec = BotCommandSpec(
        name = name,
        aliases = aliases.toList(),
        description = description,
        visible = visible,
        root = CommandNodeSpec(
            name = name,
            description = description,
            aliases = aliases.toList(),
            syntaxes = syntaxes.toList(),
            params = paramDocs.toList(),
            options = optionDocs.toList(),
            examples = exampleList.toList(),
            notes = noteList.toList(),
            children = children.toList(),
            leaf = leaf
        )
    )

}

@CommandSpecDsl
open class NodeBuilder(private val name: String) {

    var description: String = ""

    private val syntaxes = mutableListOf<CommandSyntaxSpec>()
    private val paramDocs = mutableListOf<CommandParamDoc>()
    private val optionDocs = mutableListOf<CommandOptionDoc>()
    private val exampleList = mutableListOf<String>()
    private val noteList = mutableListOf<String>()
    private var leaf: CommandLeafSpec? = null

    fun execute(block: CommandInvocation.() -> Unit) {
        leaf = EmptyLeafSpec { inv ->
            inv.block()
            1
        }
    }

    fun raw(
        argName: String = "text",
        required: Boolean = true,
        block: CommandInvocation.(String) -> Unit
    ) {
        leaf = RawLeafSpec(
            name = argName,
            required = required,
            executor = { inv ->
                inv.block(inv.rawTail)
                1
            }
        )
    }

    fun argv(block: ArgvBuilder.() -> Unit) {
        val builder = ArgvBuilder().apply(block)
        leaf = builder.buildLeaf()
    }

    fun syntax(description: String = "", vararg items: SpecSyntaxItem) {
        syntaxes += CommandSyntaxSpec(description, items.toList())
    }

    fun param(name: String, description: String = "") {
        paramDocs += CommandParamDoc(name, description)
    }

    fun optionDoc(name: String, description: String = "") {
        optionDocs += CommandOptionDoc(name, description)
    }

    fun example(vararg values: String) {
        exampleList += values
    }

    fun note(vararg values: String) {
        noteList += values
    }

    fun build(): CommandNodeSpec = CommandNodeSpec(
        name = name,
        description = description,
        syntaxes = syntaxes.toList(),
        params = paramDocs.toList(),
        options = optionDocs.toList(),
        examples = exampleList.toList(),
        notes = noteList.toList(),
        leaf = leaf
    )

}

@CommandSpecDsl
class ArgvBuilder {

    internal val positionals = mutableListOf<CommandArgSpec>()
    internal val optionSpecs = mutableListOf<CommandOptionSpec>()
    internal var executorBlock: (CommandInvocation.(ArgvParseResult) -> Unit)? = null

    fun positional(name: String, type: ArgType, block: PositionalConfigBuilder.() -> Unit = {}) {
        val config = PositionalConfigBuilder(type).apply(block)
        positionals += CommandArgSpec(
            name = name,
            type = config.type,
            required = config.required,
            defaultValue = config.default,
            greedy = config.greedy,
            description = config.description,
            choices = config.choices,
            validator = config.validator
        )
    }

    fun option(
        canonicalName: String,
        block: OptionConfigBuilder.() -> Unit
    ) {
        val config = OptionConfigBuilder(canonicalName).apply(block)
        optionSpecs += CommandOptionSpec(
            canonicalName = config.canonicalName,
            names = config.names.toList(),
            type = config.type,
            required = config.required,
            defaultValue = config.default,
            repeatable = config.repeatable,
            splitBy = config.splitBy,
            description = config.description,
            choices = config.choices,
            validator = config.validator
        )
    }

    fun execute(block: CommandInvocation.(ArgvParseResult) -> Unit) {
        executorBlock = block
    }

    fun buildLeaf(): ArgvLeafSpec {
        val block = executorBlock
            ?: throw IllegalStateException("argv execute block is required")
        return ArgvLeafSpec(
            positionals = positionals.toList(),
            options = optionSpecs.toList(),
            executor = { inv ->
                val argv = inv.argv
                    ?: throw IllegalStateException("argv not set in CommandInvocation")
                inv.block(argv)
                1
            }
        )
    }

}

@CommandSpecDsl
class PositionalConfigBuilder(val type: ArgType) {

    var required: Boolean = true
    var default: Any? = null
    var greedy: Boolean = false
    var description: String = ""
    var choices: List<String> = emptyList()
    var validator: ValueValidator? = null

}

@CommandSpecDsl
class OptionConfigBuilder(val canonicalName: String) {

    var names: List<String> = listOf("--$canonicalName")
    var type: ArgType = ArgType.StringType
    var required: Boolean = false
    var default: Any? = null
    var repeatable: Boolean = false
    var splitBy: String? = null
    var description: String = ""
    var choices: List<String> = emptyList()
    var validator: ValueValidator? = null

}

fun botCommand(
    name: String,
    block: BotCommandBuilder.() -> Unit
): BotCommandSpec = BotCommandBuilder(name).apply(block).build()

fun lit(text: String): SpecSyntaxItem =
    SpecSyntaxItem.Lit(text)

fun arg(name: String): SpecSyntaxItem =
    SpecSyntaxItem.Arg(name)

fun opt(item: SpecSyntaxItem): SpecSyntaxItem =
    SpecSyntaxItem.Opt(item)

fun oneOf(vararg items: SpecSyntaxItem): SpecSyntaxItem =
    SpecSyntaxItem.Choice(items.toList())

fun optOneOf(vararg items: SpecSyntaxItem): SpecSyntaxItem =
    SpecSyntaxItem.Opt(SpecSyntaxItem.Choice(items.toList()))

fun group(vararg items: SpecSyntaxItem): SpecSyntaxItem =
    SpecSyntaxItem.Group(items.toList())

fun concat(vararg items: SpecSyntaxItem): SpecSyntaxItem =
    SpecSyntaxItem.Concat(items.toList())
