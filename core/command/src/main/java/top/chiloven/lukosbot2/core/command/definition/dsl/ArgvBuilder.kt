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

import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.CommandArg
import top.chiloven.lukosbot2.core.command.definition.CommandInvocation
import top.chiloven.lukosbot2.core.command.definition.CommandOption
import top.chiloven.lukosbot2.core.command.definition.leaf.ArgvLeaf
import top.chiloven.lukosbot2.core.command.definition.parser.ArgvParseResult

class ArgvBuilder<S> {

    internal val positionals = mutableListOf<CommandArg>()
    internal val optionSpecs = mutableListOf<CommandOption>()
    internal var executorBlock: (CommandInvocation<S>.(ArgvParseResult) -> Int)? = null

    fun positional(
        name: String,
        type: ArgType,
        block: PositionalConfigBuilder.() -> Unit = {}
    ) {
        val config = PositionalConfigBuilder(type).apply(block)
        positionals += CommandArg(
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

    fun option(canonicalName: String, block: OptionConfigBuilder.() -> Unit) {
        val config = OptionConfigBuilder(canonicalName).apply(block)
        optionSpecs += CommandOption(
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

    fun execute(block: CommandInvocation<S>.(ArgvParseResult) -> Unit) {
        executorBlock = { argv ->
            block(argv)
            code()
        }
    }

    fun buildLeaf(): ArgvLeaf<S> {
        val block = executorBlock ?: throw IllegalStateException("argv execute block is required")
        return ArgvLeaf(
            positionals = positionals.toList(),
            options = optionSpecs.toList(),
            executor = { inv ->
                val argv = inv.argv
                    ?: throw IllegalStateException("argv not set")
                inv.block(argv)
            }
        )
    }

}
