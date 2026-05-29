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
 * Sealed hierarchy of renderable syntax items for [CommandSyntax].
 *
 * Mirrors the structure of `UsageNode.Item` to allow direct mapping
 * from DSL syntax declarations to the usage tree.
 */
sealed interface SyntaxItem {

    /** A literal token rendered as-is (e.g. `"--verbose"`). */
    data class Lit(
        val text: String
    ) : SyntaxItem

    /** A parameter placeholder rendered as `<name>`. */
    data class Arg(
        val name: String
    ) : SyntaxItem

    /** Wraps an item as optional, rendered as `[item]`. */
    data class Opt(
        val item: SyntaxItem
    ) : SyntaxItem

    /** A required choice, rendered as `(a|b)`. Requires at least 2 items. */
    data class Choice(
        val items: List<SyntaxItem>
    ) : SyntaxItem

    /** A space-separated group of items. Useful inside an [Opt] or [Choice]. */
    data class Group(
        val items: List<SyntaxItem>
    ) : SyntaxItem

    /** Concatenates items without spaces. Useful for `--key=<v>` forms. */
    data class Concat(
        val items: List<SyntaxItem>
    ) : SyntaxItem

}
