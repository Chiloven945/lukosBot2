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

import io.ktor.client.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import top.chiloven.lukosbot2.commands.bot.PlayerCommand
import top.chiloven.lukosbot2.commands.bot.motd.MotdCommand
import top.chiloven.lukosbot2.commands.bot.motd.MotdQueryService
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.util.feature.MojangApi

@Configuration(proxyBeanMethods = false)
class MinecraftCommandsConfiguration {

    @Bean
    fun mojangApi(http: HttpClient): MojangApi = MojangApi(http)

    @Bean
    fun motdQueryService(
        proxyConfigProp: ProxyConfigProp,
        http: HttpClient,
    ): MotdQueryService = MotdQueryService(proxyConfigProp, http)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["motd"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun motdCommand(motdQueryService: MotdQueryService): MotdCommand =
        MotdCommand(motdQueryService)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["player"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun playerCommand(mojangApi: MojangApi): PlayerCommand =
        PlayerCommand(mojangApi)

}
