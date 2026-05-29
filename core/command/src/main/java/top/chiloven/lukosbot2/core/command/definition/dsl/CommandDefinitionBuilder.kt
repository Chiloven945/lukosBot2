/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.core.command.definition.dsl

import top.chiloven.lukosbot2.core.command.bot.CommandSource
import top.chiloven.lukosbot2.core.command.cli.CliCmdContext
import top.chiloven.lukosbot2.core.command.definition.CommandDefinition
import top.chiloven.lukosbot2.core.command.definition.CommandInvocation
import top.chiloven.lukosbot2.core.command.definition.CommandNode
import top.chiloven.lukosbot2.core.command.definition.CommandSyntax
import top.chiloven.lukosbot2.core.command.definition.leaf.CommandLeaf
import top.chiloven.lukosbot2.core.command.definition.leaf.EmptyLeaf
import top.chiloven.lukosbot2.core.command.definition.leaf.RawLeaf
import top.chiloven.lukosbot2.core.command.definition.meta.CommandOptionDoc
import top.chiloven.lukosbot2.core.command.definition.meta.CommandParamDoc
import top.chiloven.lukosbot2.core.command.definition.SyntaxItem as SpecSyntaxItem

@CommandDsl
class CommandDefinitionBuilder<S>(private val name: String) {

    var description: String = ""
    var visible: Boolean = true

    val aliases = mutableListOf<String>()
    private val children = mutableListOf<CommandNode<S>>()
    private val syntaxes = mutableListOf<CommandSyntax>()
    private val paramDocs = mutableListOf<CommandParamDoc>()
    private val optionDocs = mutableListOf<CommandOptionDoc>()
    private val exampleList = mutableListOf<String>()
    private val noteList = mutableListOf<String>()
    private var leaf: CommandLeaf<S>? = null

    fun alias(vararg values: String) {
        aliases += values
    }

    fun literal(childName: String, block: NodeBuilder<S>.() -> Unit) {
        children += NodeBuilder<S>(childName).apply(block).build()
    }

    fun execute(block: CommandInvocation<S>.() -> Unit) {
        leaf = EmptyLeaf { inv ->
            inv.block()
            inv.code()
        }
    }

    fun raw(
        argName: String = "text",
        required: Boolean = true,
        block: CommandInvocation<S>.(String) -> Unit
    ) {
        leaf = RawLeaf(
            name = argName,
            required = required,
            executor = { inv ->
                inv.block(inv.rawTail)
                inv.code()
            }
        )
    }

    fun argv(block: ArgvBuilder<S>.() -> Unit) {
        val builder = ArgvBuilder<S>().apply(block)
        leaf = builder.buildLeaf()
    }

    fun syntax(description: String = "", vararg items: SpecSyntaxItem) {
        syntaxes += CommandSyntax(description, items.toList())
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

    fun build(): CommandDefinition<S> {
        return CommandDefinition(
            name = name,
            aliases = aliases.toList(),
            description = description,
            visible = visible,
            root = CommandNode(
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

}

inline fun <S> commandDefinition(
    name: String,
    block: CommandDefinitionBuilder<S>.() -> Unit
): CommandDefinition<S> = CommandDefinitionBuilder<S>(name).apply(block).build()

fun botCommand(
    name: String,
    block: CommandDefinitionBuilder<CommandSource>.() -> Unit
): CommandDefinition<CommandSource> = commandDefinition(name, block)

fun cliCommand(
    name: String,
    block: CommandDefinitionBuilder<CliCmdContext>.() -> Unit
): CommandDefinition<CliCmdContext> = commandDefinition(name, block)

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
