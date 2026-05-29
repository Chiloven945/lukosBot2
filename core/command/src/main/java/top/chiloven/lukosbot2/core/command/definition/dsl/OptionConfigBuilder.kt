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
import top.chiloven.lukosbot2.core.command.definition.ValueValidator

class OptionConfigBuilder(val canonicalName: String) {

    var names: List<String> = listOf("--$canonicalName")
    var type: ArgType = ArgType.StringType
    var required: Boolean = false
    var default: Any? = null
    var repeatable: Boolean = false
    var splitBy: String? = null
    var description: String = ""
    var choices: List<String> = emptyList()
    var validator: ValueValidator? = null

}
