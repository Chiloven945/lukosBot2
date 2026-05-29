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

import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand

@Service
class StartCommand : IBotCommand {

    private val commandDefinition = botCommand("start") {
        description = "开始使用 LukosBot2"
        visible = false

        execute {
            source.reply("欢迎使用 LukosBot2！这是由 @chiloven945 制作的聊天机器人，你可以在 Discord 和 Telegram 上找到他！发送 /help 查看可用命令。")
        }

        syntax("开始使用机器人")
    }

    override fun definition() = commandDefinition

}
