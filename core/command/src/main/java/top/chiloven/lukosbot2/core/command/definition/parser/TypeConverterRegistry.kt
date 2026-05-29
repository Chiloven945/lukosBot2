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

import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.CommandParseException
import kotlin.reflect.KClass

/**
 * Registry of type converters for [ArgvParser].
 *
 * Provides built-in conversion for `String`, `Int`, `Long`,
 * `Boolean`, and `EnumType` (choices). Custom types can be registered
 * via [register] to support domain-specific argument types.
 *
 * ### Usage
 * ```kotlin
 * val registry = TypeConverterRegistry()
 * registry.register(MinecraftServerAddress::class) { raw ->
 *     MinecraftServerAddress.parse(raw)
 * }
 * ```
 */
class TypeConverterRegistry {

    private val converters = mutableMapOf<KClass<*>, (String) -> Any>()

    /**
     * Registers a converter for the given class.
     *
     * @param type the target class
     * @param converter a function that converts a raw string to the target type
     */
    fun <T : Any> register(type: KClass<T>, converter: (String) -> T) {
        @Suppress("UNCHECKED_CAST")
        converters[type] = converter as (String) -> Any
    }

    /**
     * Converts a raw string to the target type based on the given [ArgType].
     *
     * @param type the expected argument type
     * @param raw the raw string value
     * @return the converted value
     * @throws CommandParseException if the value is invalid for the type
     * @throws NumberFormatException for invalid Int/Long values
     * @throws IllegalStateException if no converter is registered for a CustomType
     */
    fun convert(type: ArgType, raw: String): Any {
        return try {
            when (type) {
                ArgType.StringType -> raw
                ArgType.IntType -> raw.toInt()
                ArgType.LongType -> raw.toLong()
                ArgType.BooleanType -> raw.toBooleanStrict()
                is ArgType.EnumType -> {
                    if (raw !in type.values) {
                        throw CommandParseException("参数值无效：$raw，可选值：${type.values.joinToString(", ")}")
                    }
                    raw
                }

                is ArgType.CustomType<*> -> {
                    val converter = converters[type.klass]
                        ?: throw IllegalStateException("未注册的类型转换器：${type.klass}")
                    converter(raw)
                }
            }
        } catch (e: NumberFormatException) {
            throw CommandParseException(
                message = when (type) {
                    ArgType.IntType -> "参数需要整数，收到：$raw"
                    ArgType.LongType -> "参数需要整数，收到：$raw"
                    else -> "参数格式无效：$raw"
                },
                cause = e
            )
        }
    }

    companion object {

        /** Shared instance with built-in converters (String, Int, Long, Boolean, Enum). */
        val default = TypeConverterRegistry()

    }

}
