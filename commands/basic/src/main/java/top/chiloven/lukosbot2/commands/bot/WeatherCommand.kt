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
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["weather"],
    havingValue = "true",
    matchIfMissing = true
)
class WeatherCommand : IBotCommand {

    private val commandDefinition = botCommand("weather") {
        description = "查询天气（暂未开放）"

        syntax("该命令暂未开放")

        execute {
            source.reply("天气查询功能尚未接入，请关注后续更新。")
        }

        note("天气查询功能尚未接入，暂时无法使用。")
    }

    override fun definition() = commandDefinition

}
