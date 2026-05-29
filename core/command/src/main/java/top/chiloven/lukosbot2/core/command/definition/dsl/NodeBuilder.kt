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

import top.chiloven.lukosbot2.core.command.definition.CommandInvocation
import top.chiloven.lukosbot2.core.command.definition.CommandNode
import top.chiloven.lukosbot2.core.command.definition.CommandSyntax
import top.chiloven.lukosbot2.core.command.definition.SyntaxItem
import top.chiloven.lukosbot2.core.command.definition.leaf.CommandLeaf
import top.chiloven.lukosbot2.core.command.definition.leaf.EmptyLeaf
import top.chiloven.lukosbot2.core.command.definition.leaf.RawLeaf
import top.chiloven.lukosbot2.core.command.definition.meta.CommandOptionDoc
import top.chiloven.lukosbot2.core.command.definition.meta.CommandParamDoc

open class NodeBuilder<S>(
    private val name: String
) {

    var description: String = ""

    private val children = mutableListOf<CommandNode<S>>()
    private val syntaxes = mutableListOf<CommandSyntax>()
    private val paramDocs = mutableListOf<CommandParamDoc>()
    private val optionDocs = mutableListOf<CommandOptionDoc>()
    private val exampleList = mutableListOf<String>()
    private val noteList = mutableListOf<String>()
    private var leaf: CommandLeaf<S>? = null

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
        leaf = ArgvBuilder<S>().apply(block).buildLeaf()
    }

    fun syntax(description: String = "", vararg items: SyntaxItem) {
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

    fun build(): CommandNode<S> = CommandNode(
        name = name,
        description = description,
        syntaxes = syntaxes.toList(),
        params = paramDocs.toList(),
        options = optionDocs.toList(),
        examples = exampleList.toList(),
        notes = noteList.toList(),
        children = children.toList(),
        leaf = leaf
    )

}
