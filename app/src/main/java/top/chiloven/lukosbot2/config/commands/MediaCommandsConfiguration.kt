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
import top.chiloven.lukosbot2.commands.bot.cave.CaveCommand
import top.chiloven.lukosbot2.commands.bot.cave.CaveService
import top.chiloven.lukosbot2.commands.bot.e621.E621Api
import top.chiloven.lukosbot2.commands.bot.e621.E621Command
import top.chiloven.lukosbot2.commands.bot.kemono.KemonoAPI
import top.chiloven.lukosbot2.commands.bot.kemono.KemonoCommand
import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.core.MediaRefLoader
import top.chiloven.lukosbot2.core.auth.AuthorizationService
import top.chiloven.lukosbot2.core.policy.PolicyService
import top.chiloven.lukosbot2.core.state.store.IStateStore
import top.chiloven.lukosbot2.util.DownloadClient

@Configuration(proxyBeanMethods = false)
class MediaCommandsConfiguration {

    @Bean
    fun caveService(
        store: IStateStore,
        mediaRefLoader: MediaRefLoader,
        props: AppProperties,
    ): CaveService = CaveService(store, mediaRefLoader, props)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["cave"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun caveCommand(
        caveService: CaveService,
        authz: AuthorizationService,
    ): CaveCommand = CaveCommand(caveService, authz)

    @Bean
    fun e621Api(http: HttpClient): E621Api = E621Api(http)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["e621"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun e621Command(
        e621Api: E621Api,
        http: HttpClient,
        policyService: PolicyService,
        props: AppProperties,
    ): E621Command = E621Command(e621Api, http, policyService, props)

    @Bean
    fun kemonoApi(http: HttpClient): KemonoAPI = KemonoAPI(http)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["kemono"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun kemonoCommand(
        props: AppProperties,
        kemonoApi: KemonoAPI,
        http: HttpClient,
        downloadClient: DownloadClient,
    ): KemonoCommand = KemonoCommand(props, kemonoApi, http, downloadClient)

}
