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
package top.chiloven.lukosbot2.commands.definition.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.core.command.bot.CommandSource
import top.chiloven.lukosbot2.core.command.definition.*
import top.chiloven.lukosbot2.core.command.definition.bridge.CommandUsageMapper
import top.chiloven.lukosbot2.core.command.definition.leaf.ArgvLeaf
import top.chiloven.lukosbot2.core.command.definition.leaf.EmptyLeaf
import top.chiloven.lukosbot2.core.command.definition.leaf.RawLeaf
import top.chiloven.lukosbot2.core.command.definition.leaf.TreeLeaf

class CommandUsageMapperTest {

    private val noop =
        CommandExecutor<CommandSource> { 1 }

    @Test
    fun ping_usage() {
        val spec = CommandDefinition(
            name = "ping",
            description = "return bot status",
            root = CommandNode(
                name = "ping",
                syntaxes = listOf(
                    CommandSyntax(
                        description = "Check status"
                    )
                ),
                examples = listOf("ping"),
                leaf = EmptyLeaf(noop)
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertEquals("ping", node.name)
        assertEquals(1, node.examples.size)
    }

    @Test
    fun dice_usage() {
        val spec = CommandDefinition(
            name = "dice",
            description = "roll dice",
            root = CommandNode(
                name = "dice",
                examples = listOf("dice", "dice 3"),
                leaf = ArgvLeaf(
                    positionals = listOf(
                        CommandArg(
                            "count",
                            ArgType.LongType,
                            required = false,
                            defaultValue = 1L
                        )
                    ),
                    options = emptyList(),
                    executor = noop
                )
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertEquals("dice", node.name)
        assertTrue(node.syntaxes.first().tailText().contains("count"))
    }

    @Test
    fun ip_usage() {
        val spec = CommandDefinition(
            name = "ip",
            description = "query IP",
            root = CommandNode(
                name = "ip",
                examples = listOf("ip 1.1.1.1"),
                leaf = ArgvLeaf(
                    positionals = listOf(
                        CommandArg(
                            "ip",
                            ArgType.StringType,
                            required = true
                        )
                    ),
                    options = listOf(
                        CommandOption(
                            "providers",
                            listOf("--provider"),
                            ArgType.StringType,
                            splitBy = ","
                        )
                    ),
                    executor = noop
                )
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertTrue(node.syntaxes.first().tailText().contains("ip"))
    }

    @Test
    fun github_nested_usage() {
        val spec = CommandDefinition(
            name = "github",
            aliases = listOf("gh"),
            description = "GitHub",
            root = CommandNode(
                name = "github",
                children = listOf(
                    CommandNode(
                        name = "user",
                        examples = listOf("github user x"),
                        leaf = RawLeaf("username", required = true, executor = noop)
                    ),
                    CommandNode(
                        name = "search",
                        examples = listOf("github search x"),
                        leaf = ArgvLeaf(
                            positionals = listOf(
                                CommandArg(
                                    "keyword",
                                    ArgType.StringType,
                                    required = true,
                                    greedy = true
                                )
                            ),
                            options = listOf(
                                CommandOption(
                                    "top",
                                    listOf("--top"),
                                    ArgType.IntType
                                )
                            ),
                            executor = noop
                        )
                    )
                )
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertEquals("github", node.name)
        assertEquals(listOf("gh"), node.aliases)
        assertEquals(2, node.children.size)
    }

    @Test
    fun argv_options_render() {
        val spec = CommandDefinition(
            name = "cmd",
            description = "test",
            root = CommandNode(
                name = "cmd",
                leaf = ArgvLeaf(
                    positionals = listOf(
                        CommandArg(
                            "input",
                            ArgType.StringType,
                            required = true
                        )
                    ),
                    options = listOf(
                        CommandOption(
                            "verbose",
                            listOf("-v"),
                            ArgType.BooleanType
                        ),
                        CommandOption(
                            "count",
                            listOf("-n"),
                            ArgType.IntType
                        )
                    ),
                    executor = noop
                )
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)
        val syntaxText = node.syntaxes.first().tailText()

        assertTrue(syntaxText.contains("input"))
    }

    @Test
    fun aliases_render() {
        val spec = CommandDefinition(
            name = "github",
            aliases = listOf("gh", "git"),
            description = "GitHub tools",
            root = CommandNode(
                name = "github",
                leaf = EmptyLeaf(noop)
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertEquals(2, node.aliases.size)
    }

    @Test
    fun tree_leaf_usage() {
        val spec = CommandDefinition(
            name = "calc",
            description = "calculate",
            root = CommandNode(
                name = "calc",
                leaf = TreeLeaf(
                    arguments = listOf(
                        CommandArg(
                            "a",
                            ArgType.IntType,
                            required = true
                        ),
                        CommandArg(
                            "b",
                            ArgType.IntType,
                            required = false
                        )
                    ),
                    executor = noop
                )
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)
        val text = node.syntaxes.first().tailText()

        assertTrue(text.contains("<a>"))
        assertTrue(text.contains("[<b>]"))
    }

    @Test
    fun empty_leaf_no_tail() {
        val spec = CommandDefinition(
            name = "ping",
            description = "pong",
            root = CommandNode(
                name = "ping",
                leaf = EmptyLeaf(noop)
            )
        )
        val node = CommandUsageMapper.toUsageNode(spec)

        assertEquals(1, node.syntaxes.size)
        assertTrue(node.syntaxes.first().tailText().isEmpty())
    }

}
