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

/**
 * Exception thrown when command input fails to parse according to the command definition.
 *
 * Typically thrown by the parser layer (`ArgvParser`, `ShellWords`)
 * and caught by the runtime to produce end-user-facing error messages.
 *
 * @param message human-readable error cause
 * @param input the raw input that caused the error (optional)
 * @param cursor the character position of the error (optional)
 */
class CommandParseException(
    message: String,
    val input: String? = null,
    val cursor: Int? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
