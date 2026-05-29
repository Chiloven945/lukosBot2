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
package top.chiloven.lukosbot2.core.command.definition.parser

import top.chiloven.lukosbot2.core.command.definition.*

/**
 * Parses argv-style tokens into named positional and option values.
 *
 * Input is expected to already be tokenized (e.g. by [ShellWords]).
 * Options are detected by their `--` / `-` prefixes and matched
 * against the registered option specs. Remaining tokens are treated as
 * positional arguments and matched against the positional specs in order.
 *
 * ### Supported option forms
 *
 *   - `--name=value` — inline equals sign
 *   - `--name value` — space-separated value
 *   - `-n value` — short option
 *   - `-f` — boolean flag (sets `true`, consumes no following token)
 *
 *
 * ### Error handling
 * All errors throw [CommandParseException] with Chinese-language messages.
 */
object ArgvParser {

    /**
     * Parses tokenized input into an [ArgvParseResult].
     *
     * @param tokens the tokenized command tail (from [ShellWords])
     * @param positionals the positional argument specifications
     * @param options the option/flag specifications
     * @param converters the type conversion registry
     * @return the parsed result with named values and raw positional strings
     * @throws CommandParseException on structural or validation errors
     */
    fun parse(
        tokens: List<String>,
        positionals: List<CommandArg>,
        options: List<CommandOption>,
        converters: TypeConverterRegistry = TypeConverterRegistry.default
    ): ArgvParseResult {
        val values = mutableMapOf<String, Any?>()
        val rawPositionals = mutableListOf<String>()

        val longOptionIndex = mutableMapOf<String, CommandOption>()
        val shortOptionIndex = mutableMapOf<String, CommandOption>()
        for (opt in options) {
            for (name in opt.names) {
                when {
                    name.startsWith("--") -> longOptionIndex[name] = opt
                    name.length == 2 && name.startsWith("-") -> shortOptionIndex[name] = opt
                }
            }
        }

        var i = 0
        var stopOptions = false
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                !stopOptions && token == "--" -> {
                    stopOptions = true
                }

                !stopOptions && token.startsWith("--") -> {
                    i = parseLongOption(tokens, i, token, longOptionIndex, values, converters)
                }

                !stopOptions && token.startsWith("-")
                        && token.length == 2
                        && !isNegativeNumber(token) -> {
                    i = parseShortOption(tokens, i, token, shortOptionIndex, values, converters)
                }

                else -> {
                    rawPositionals.add(token)
                }
            }
            i++
        }

        processPositionals(values, rawPositionals, positionals, converters)
        applyDefaults(values, positionals, options)

        return ArgvParseResult(
            values = values,
            positionals = rawPositionals
        )
    }

    private fun isNegativeNumber(token: String): Boolean {
        if (token.length < 2) return false
        if (token[0] != '-') return false
        return token.substring(1).all { it.isDigit() }
    }

    private fun parseLongOption(
        tokens: List<String>,
        idx: Int,
        token: String,
        index: Map<String, CommandOption>,
        values: MutableMap<String, Any?>,
        converters: TypeConverterRegistry
    ): Int {
        val eqIdx = token.indexOf('=')
        val name: String
        val inlineValue: String?
        if (eqIdx >= 0) {
            name = token.substring(0, eqIdx)
            inlineValue = token.substring(eqIdx + 1)
        } else {
            name = token
            inlineValue = null
        }

        val spec = index[name]
            ?: throw CommandParseException("未知参数：$name")

        if (spec.type == ArgType.BooleanType) {
            if (inlineValue != null) {
                storeValue(
                    values, spec,
                    parseBooleanFlag(inlineValue, name)
                )
            } else {
                storeValue(values, spec, true)
            }
            return idx
        }

        val rawValue = if (inlineValue != null) {
            inlineValue
        } else {
            if (idx + 1 >= tokens.size || startsOption(tokens[idx + 1])) {
                throw CommandParseException("参数 $name 需要一个值")
            }
            tokens[idx + 1]
        }

        storeConvertedValue(values, spec, rawValue, converters)

        return if (inlineValue == null) idx + 1 else idx
    }

    private fun parseBooleanFlag(raw: String, name: String): Boolean {
        return when (raw) {
            "true" -> true
            "false" -> false
            else -> throw CommandParseException(
                "参数 $name 是布尔类型，只接受 true 或 false，收到：$raw"
            )
        }
    }

    private fun parseShortOption(
        tokens: List<String>,
        idx: Int,
        token: String,
        index: Map<String, CommandOption>,
        values: MutableMap<String, Any?>,
        converters: TypeConverterRegistry
    ): Int {
        val spec = index[token]
            ?: throw CommandParseException("未知参数：$token")

        if (spec.type == ArgType.BooleanType) {
            storeValue(values, spec, true)
            return idx
        }

        if (idx + 1 >= tokens.size || startsOption(tokens[idx + 1])) {
            throw CommandParseException("参数 $token 需要一个值")
        }

        storeConvertedValue(values, spec, tokens[idx + 1], converters)
        return idx + 1
    }

    private fun startsOption(token: String): Boolean {
        if (token == "--") return true
        if (token.startsWith("--")) return true
        return token.startsWith("-") && token.length == 2 && !isNegativeNumber(token)
    }

    private fun storeConvertedValue(
        values: MutableMap<String, Any?>,
        spec: CommandOption,
        rawValue: String,
        converters: TypeConverterRegistry
    ) {
        if (spec.splitBy != null) {
            val parts = rawValue.split(spec.splitBy)
                .filter { it.isNotEmpty() }
            if (parts.isEmpty()) {
                throw CommandParseException("参数 ${spec.canonicalName} 的值不能为空")
            }
            val convertedParts = parts.map {
                convertAndValidate(spec.type, it, spec.choices, spec.validator, converters)
            }
            if (spec.repeatable) {
                @Suppress("UNCHECKED_CAST")
                val list = values.getOrPut(spec.canonicalName) {
                    mutableListOf<Any>()
                } as MutableList<Any>

                list.addAll(convertedParts)
                values[spec.canonicalName] = list.toList()
            } else {
                values[spec.canonicalName] = convertedParts
            }
        } else {
            val converted = convertAndValidate(spec.type, rawValue, spec.choices, spec.validator, converters)
            storeValue(values, spec, converted)
        }
    }

    private fun storeValue(
        values: MutableMap<String, Any?>,
        spec: CommandOption,
        converted: Any
    ) {
        if (spec.repeatable) {
            @Suppress("UNCHECKED_CAST")
            val list = values.getOrPut(spec.canonicalName) { mutableListOf<Any>() } as MutableList<Any>
            list.add(converted)
            values[spec.canonicalName] = list.toList()
        } else {
            values[spec.canonicalName] = converted
        }
    }

    private fun processPositionals(
        values: MutableMap<String, Any?>,
        rawPositionals: List<String>,
        specs: List<CommandArg>,
        converters: TypeConverterRegistry
    ) {
        var posIdx = 0
        var specIdx = 0

        while (specIdx < specs.size && posIdx < rawPositionals.size) {
            val spec = specs[specIdx]
            if (spec.greedy) {
                val remaining = rawPositionals.subList(posIdx, rawPositionals.size)
                val joined = remaining.joinToString(" ")
                val converted = convertAndValidate(spec.type, joined, spec.choices, spec.validator, converters)
                values[spec.name] = converted
                posIdx = rawPositionals.size
                specIdx++
                break
            } else {
                val raw = rawPositionals[posIdx]
                val converted = convertAndValidate(
                    spec.type, raw, spec.choices, spec.validator, converters
                )
                values[spec.name] = converted
                posIdx++
                specIdx++
            }
        }

        while (specIdx < specs.size) {
            val spec = specs[specIdx]
            if (spec.required && spec.defaultValue == null) {
                throw CommandParseException("缺少必填参数：${spec.name}")
            }
            specIdx++
        }

        if (posIdx < rawPositionals.size) {
            throw CommandParseException(
                "参数过多，无法解析：" +
                        rawPositionals.subList(posIdx, rawPositionals.size).joinToString(" ")
            )
        }
    }

    private fun applyDefaults(
        values: MutableMap<String, Any?>,
        positionals: List<CommandArg>,
        options: List<CommandOption>
    ) {
        for (spec in positionals) {
            if (spec.name !in values && spec.defaultValue != null) {
                values[spec.name] = spec.defaultValue
            }
        }
        for (spec in options) {
            if (spec.canonicalName !in values) {
                if (spec.defaultValue != null) {
                    values[spec.canonicalName] = spec.defaultValue
                } else if (spec.required) {
                    throw CommandParseException("缺少必填参数：${spec.canonicalName}")
                }
            }
        }
    }

    private fun convertAndValidate(
        type: ArgType,
        raw: String,
        choices: List<String>,
        validator: ValueValidator?,
        converters: TypeConverterRegistry
    ): Any {
        val converted = converters.convert(type, raw)
        if (choices.isNotEmpty() && converted.toString() !in choices) {
            throw CommandParseException(
                "参数值无效：$raw，可选值：${choices.joinToString(", ")}"
            )
        }
        if (validator != null) {
            val error = validator.validate(converted)
            if (error != null) {
                throw CommandParseException(error)
            }
        }
        return converted
    }

}
