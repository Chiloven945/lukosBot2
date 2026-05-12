package top.chiloven.lukosbot2.commands.spec.parser

import top.chiloven.lukosbot2.commands.spec.*

object ArgvParser {

    fun parse(
        tokens: List<String>,
        positionals: List<CommandArgSpec>,
        options: List<CommandOptionSpec>,
        converters: TypeConverterRegistry = TypeConverterRegistry.default
    ): ArgvParseResult {
        val values = mutableMapOf<String, Any?>()
        val rawPositionals = mutableListOf<String>()

        val longOptionIndex = mutableMapOf<String, CommandOptionSpec>()
        val shortOptionIndex = mutableMapOf<String, CommandOptionSpec>()
        for (opt in options) {
            for (name in opt.names) {
                when {
                    name.startsWith("--") -> longOptionIndex[name] = opt
                    name.length == 2 && name.startsWith("-") -> shortOptionIndex[name] = opt
                }
            }
        }

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.startsWith("--") -> {
                    i = parseLongOption(tokens, i, token, longOptionIndex, values, converters)
                }

                token.startsWith("-") && token.length == 2 -> {
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

    private fun parseLongOption(
        tokens: List<String>,
        idx: Int,
        token: String,
        index: Map<String, CommandOptionSpec>,
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
            storeValue(values, spec, true)
            return idx
        }

        val rawValue = if (inlineValue != null) {
            inlineValue
        } else {
            if (idx + 1 >= tokens.size) {
                throw CommandParseException("参数 $name 需要一个值")
            }
            tokens[idx + 1]
        }

        storeConvertedValue(values, spec, rawValue, converters)

        return if (inlineValue == null) idx + 1 else idx
    }

    private fun parseShortOption(
        tokens: List<String>,
        idx: Int,
        token: String,
        index: Map<String, CommandOptionSpec>,
        values: MutableMap<String, Any?>,
        converters: TypeConverterRegistry
    ): Int {
        val spec = index[token]
            ?: throw CommandParseException("未知参数：$token")

        if (spec.type == ArgType.BooleanType) {
            storeValue(values, spec, true)
            return idx
        }

        if (idx + 1 >= tokens.size) {
            throw CommandParseException("参数 $token 需要一个值")
        }

        storeConvertedValue(values, spec, tokens[idx + 1], converters)
        return idx + 1
    }

    private fun storeConvertedValue(
        values: MutableMap<String, Any?>,
        spec: CommandOptionSpec,
        rawValue: String,
        converters: TypeConverterRegistry
    ) {
        if (spec.splitBy != null) {
            val parts = rawValue.split(spec.splitBy)
            val convertedParts = parts.map {
                convertAndValidate(spec.type, it, spec.choices, spec.validator)
            }
            if (spec.repeatable) {
                @Suppress("UNCHECKED_CAST")
                val list = values.getOrPut(spec.canonicalName) { mutableListOf<Any>() } as MutableList<Any>
                list.addAll(convertedParts)
            } else {
                values[spec.canonicalName] = convertedParts
            }
        } else {
            val converted = convertAndValidate(
                spec.type, rawValue, spec.choices, spec.validator
            )
            storeValue(values, spec, converted)
        }
    }

    private fun storeValue(
        values: MutableMap<String, Any?>,
        spec: CommandOptionSpec,
        converted: Any
    ) {
        if (spec.repeatable) {
            @Suppress("UNCHECKED_CAST")
            val list = values.getOrPut(spec.canonicalName) { mutableListOf<Any>() } as MutableList<Any>
            list.add(converted)
        } else {
            values[spec.canonicalName] = converted
        }
    }

    private fun processPositionals(
        values: MutableMap<String, Any?>,
        rawPositionals: List<String>,
        specs: List<CommandArgSpec>,
        converters: TypeConverterRegistry
    ) {
        var posIdx = 0
        var specIdx = 0

        while (specIdx < specs.size && posIdx < rawPositionals.size) {
            val spec = specs[specIdx]
            if (spec.greedy) {
                val remaining = rawPositionals.subList(posIdx, rawPositionals.size)
                val joined = remaining.joinToString(" ")
                val converted = convertAndValidate(
                    spec.type, joined, spec.choices, spec.validator
                )
                values[spec.name] = converted
                posIdx = rawPositionals.size
                specIdx++
                break
            } else {
                val raw = rawPositionals[posIdx]
                val converted = convertAndValidate(
                    spec.type, raw, spec.choices, spec.validator
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
        positionals: List<CommandArgSpec>,
        options: List<CommandOptionSpec>
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
        validator: ValueValidator?
    ): Any {
        val converted = TypeConverterRegistry.default.convert(type, raw)
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
