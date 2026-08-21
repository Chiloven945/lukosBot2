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
import top.chiloven.lukosbot2.commands.bot.bilibili.BilibiliApi
import top.chiloven.lukosbot2.commands.bot.bilibili.BilibiliCommand
import top.chiloven.lukosbot2.commands.bot.bilibili.BilibiliQueryService
import top.chiloven.lukosbot2.commands.bot.github.GitHubCommand
import top.chiloven.lukosbot2.commands.bot.ip.IpCommand
import top.chiloven.lukosbot2.commands.bot.ip.IpQueryService
import top.chiloven.lukosbot2.commands.bot.ip.provider.IIpProvider
import top.chiloven.lukosbot2.commands.bot.ip.provider.impl.IpQueryIoProvider
import top.chiloven.lukosbot2.commands.bot.ip.provider.impl.IpSbProvider
import top.chiloven.lukosbot2.commands.bot.music.MusicCommand
import top.chiloven.lukosbot2.config.CommandConfigProp

@Configuration(proxyBeanMethods = false)
class IntegrationCommandsConfiguration {

    @Bean
    fun bilibiliApi(http: HttpClient): BilibiliApi = BilibiliApi(http)

    @Bean
    fun bilibiliQueryService(bilibiliApi: BilibiliApi): BilibiliQueryService =
        BilibiliQueryService(bilibiliApi)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["bilibili"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun bilibiliCommand(bilibiliQueryService: BilibiliQueryService): BilibiliCommand =
        BilibiliCommand(bilibiliQueryService)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["github"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun gitHubCommand(ccp: CommandConfigProp, httpClient: HttpClient): GitHubCommand =
        GitHubCommand(ccp, httpClient)

    @Bean
    fun ipQueryIoProvider(http: HttpClient): IpQueryIoProvider = IpQueryIoProvider(http)

    @Bean
    fun ipSbProvider(http: HttpClient): IpSbProvider = IpSbProvider(http)

    @Bean
    fun ipQueryService(providers: List<IIpProvider>): IpQueryService =
        IpQueryService(providers)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["ip"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun ipCommand(ipQueryService: IpQueryService): IpCommand =
        IpCommand(ipQueryService)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["music"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun musicCommand(ccp: CommandConfigProp, httpClient: HttpClient): MusicCommand =
        MusicCommand(ccp, httpClient)

}
