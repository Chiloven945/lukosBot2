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
package top.chiloven.lukosbot2.config.commands

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import top.chiloven.lukosbot2.commands.bot.*
import top.chiloven.lukosbot2.config.CommandConfigProp
import top.chiloven.lukosbot2.core.BotCoroutineRuntime
import top.chiloven.lukosbot2.core.MessageSenderHub

@Configuration(proxyBeanMethods = false)
class BasicCommandsConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["ping"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun pingCommand(): PingCommand = PingCommand()

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["echo"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun echoCommand(): EchoCommand = EchoCommand()

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["coin"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun coinCommand(): CoinCommand = CoinCommand()

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["dice"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun diceCommand(): DiceCommand = DiceCommand()

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["luck"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun luckCommand(): LuckCommand = LuckCommand()

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["twentyfour"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun twentyFourCommand(
        senderHub: MessageSenderHub,
        runtime: BotCoroutineRuntime,
        config: CommandConfigProp,
    ): TwentyFourCommand = TwentyFourCommand(senderHub, runtime, config)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["weather"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun weatherCommand(): WeatherCommand = WeatherCommand()

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["whois"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun whoisCommand(): WhoisCommand = WhoisCommand()

    @Bean
    fun startCommand(): StartCommand = StartCommand()

}
