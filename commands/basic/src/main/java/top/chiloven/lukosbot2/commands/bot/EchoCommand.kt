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
package top.chiloven.lukosbot2.commands.bot

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.definition.dsl.arg
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["echo"],
    havingValue = "true",
    matchIfMissing = true
)
class EchoCommand : IBotCommand {

    private val commandDefinition = botCommand("echo") {
        description = "原样返回文本"

        raw("text", required = false) { text ->
            if (text.isBlank()) source.reply("请输入要回显的文本。用法：/echo <text>")
            else source.reply(text)
        }

        syntax("回显输入的文本", arg("text"))
        param("text", "要回显的文本（支持空格）")

        example("echo hello")
    }

    override fun definition() = commandDefinition

}
