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
package top.chiloven.lukosbot2.core.command.definition.meta

/**
 * Documented parameter entry for the usage tree.
 *
 * Appears in the "Parameters" section of rendered help. Automatically
 * generated from positional argument specs in argv/tree leaves, or can be
 * hand-written to document protocol-specific parameters.
 *
 * @param name the token label (without angle brackets)
 * @param description human-readable explanation
 */
data class CommandParamDoc(
    val name: String,
    val description: String
)
