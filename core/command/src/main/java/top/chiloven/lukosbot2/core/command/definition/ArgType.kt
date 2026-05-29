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
package top.chiloven.lukosbot2.core.command.definition

import kotlin.reflect.KClass

/**
 * Sealed hierarchy of argument types supported by the parser.
 *
 * The parser uses this type information to convert raw string tokens
 * into typed JVM values. Custom types can be registered via
 * `TypeConverterRegistry`.
 */
sealed interface ArgType {

    /** Passes the raw string through unchanged. */
    data object StringType : ArgType

    /** Converts via `String.toInt()`. */
    data object IntType : ArgType

    /** Converts via `String.toLong()`. */
    data object LongType : ArgType

    /** Converts via `String.toBooleanStrict()`. Flags set `true`; absence sets default (usually `false`). */
    data object BooleanType : ArgType

    /** Validates that the raw value is one of the allowed choices. */
    data class EnumType(
        val values: List<String>
    ) : ArgType

    /** Delegates to a user-registered converter function. */
    data class CustomType<T : Any>(
        val klass: KClass<T>
    ) : ArgType

}
