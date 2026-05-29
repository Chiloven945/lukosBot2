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
package top.chiloven.lukosbot2.core.command.definition.bridge

import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.core.command.definition.*
import top.chiloven.lukosbot2.core.command.definition.leaf.*

/**
 * Maps a [top.chiloven.lukosbot2.core.command.definition.CommandDefinition] to a [UsageNode] for help rendering.
 *
 * This is the bridge between the command definition DSL and the existing
 * `UsageNode`-based help system. It translates:
 * - Hand-written syntax lines
 * - Auto-generated syntax from leaf types (empty/raw/argv/tree)
 * - Documentation metadata (params, options, examples, notes)
 * - Aliases and recursive child nodes
 */
object CommandUsageMapper {

    /**
     * Converts a command definition to a usage node tree.
     *
     * @param spec the command definition
     * @return the root usage node
     */
    fun <S> toUsageNode(spec: CommandDefinition<S>): UsageNode {
        return mapNode(spec.root, spec.name, spec.description, spec.aliases)
    }

    private fun <S> mapNode(
        node: CommandNode<S>,
        name: String,
        description: String,
        aliases: List<String>
    ): UsageNode {
        val builder = UsageNode.root(name)
            .description(description)
            .alias(aliases)
            .example(*node.examples.toTypedArray())
            .note(*node.notes.toTypedArray())

        for (syntax in node.syntaxes) {
            builder.syntax(
                syntax.description,
                *syntax.items.map { mapSyntaxItem(it) }.toTypedArray()
            )
        }

        if (node.syntaxes.isEmpty() && node.leaf != null) {
            val autoItems = generateSyntaxItems(node.leaf)
            if (autoItems.isNotEmpty()) {
                builder.syntax("", *autoItems.toTypedArray())
            } else {
                builder.syntax("")
            }
        }

        for (param in node.params) {
            builder.param(param.name, param.description)
        }

        for (opt in node.options) {
            builder.option(opt.name, opt.description)
        }

        val autoParams = generateAutoParams(node)
        for ((item, desc) in autoParams) {
            builder.parameter(item, desc)
        }

        val autoOptions = generateAutoOptions(node)
        for ((item, desc) in autoOptions) {
            builder.option(item, desc)
        }

        for (child in node.children) {
            builder.child(
                mapNode(
                    child,
                    child.name,
                    child.description,
                    child.aliases
                )
            )
        }

        return builder.build()
    }

    private fun <S> generateSyntaxItems(leaf: CommandLeaf<S>): List<UsageNode.Item> {
        return when (leaf) {
            is EmptyLeaf -> emptyList()
            is RawLeaf -> {
                val item = UsageNode.arg(leaf.name)
                listOf(if (leaf.required) item else UsageNode.opt(item))
            }

            is ArgvLeaf -> {
                val items = mutableListOf<UsageNode.Item>()
                for (pos in leaf.positionals) {
                    val item = UsageNode.arg(pos.name)
                    items.add(if (pos.required) item else UsageNode.opt(item))
                }
                for (opt in leaf.options) {
                    items.add(optionToSyntaxItem(opt))
                }
                items
            }

            is TreeLeaf -> {
                leaf.arguments.map { arg ->
                    val item = UsageNode.arg(arg.name)
                    if (arg.required) item else UsageNode.opt(item)
                }
            }
        }
    }

    private fun optionToSyntaxItem(opt: CommandOption): UsageNode.Item {
        val displayName = opt.names.firstOrNull()
            ?: "--${opt.canonicalName}"

        val item: UsageNode.Item = if (opt.type == ArgType.BooleanType) {
            UsageNode.lit(displayName)
        } else {
            UsageNode.concat(
                UsageNode.lit("$displayName="),
                UsageNode.arg(opt.canonicalName)
            )
        }

        return if (opt.required) item else UsageNode.opt(item)
    }

    private fun <S> generateAutoParams(node: CommandNode<S>): List<Pair<UsageNode.Item, String>> {
        val leaf = node.leaf ?: return emptyList()
        return when (leaf) {
            is ArgvLeaf -> leaf.positionals.map { pos ->
                UsageNode.arg(pos.name) to pos.description
            }

            is TreeLeaf -> leaf.arguments.map { arg ->
                UsageNode.arg(arg.name) to arg.description
            }

            is EmptyLeaf -> emptyList()
            is RawLeaf -> emptyList()
        }
    }

    private fun <S> generateAutoOptions(node: CommandNode<S>): List<Pair<UsageNode.Item, String>> {
        val leaf = node.leaf
        if (leaf !is ArgvLeaf) return emptyList()

        return leaf.options.map { opt ->
            val displayName = opt.names.firstOrNull()
                ?: "--${opt.canonicalName}"

            val item: UsageNode.Item = if (opt.type == ArgType.BooleanType) {
                UsageNode.lit(displayName)
            } else {
                UsageNode.concat(
                    UsageNode.lit("$displayName="),
                    UsageNode.arg(opt.canonicalName)
                )
            }

            item to opt.description
        }
    }

    private fun mapSyntaxItem(item: SyntaxItem): UsageNode.Item {
        return when (item) {
            is SyntaxItem.Lit -> UsageNode.lit(item.text)
            is SyntaxItem.Arg -> UsageNode.arg(item.name)
            is SyntaxItem.Opt -> UsageNode.opt(mapSyntaxItem(item.item))
            is SyntaxItem.Choice -> UsageNode.oneOf(
                *item.items.map { mapSyntaxItem(it) }.toTypedArray()
            )

            is SyntaxItem.Group -> UsageNode.group(
                *item.items.map { mapSyntaxItem(it) }.toTypedArray()
            )

            is SyntaxItem.Concat -> UsageNode.concat(
                *item.items.map { mapSyntaxItem(it) }.toTypedArray()
            )
        }
    }

}
