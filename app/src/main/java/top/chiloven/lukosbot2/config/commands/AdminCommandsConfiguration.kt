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

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import top.chiloven.lukosbot2.commands.bot.AdminCommand
import top.chiloven.lukosbot2.commands.bot.HelpCommand
import top.chiloven.lukosbot2.commands.bot.PrefCommand
import top.chiloven.lukosbot2.commands.bot.ServiceCommand
import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.core.auth.AuthorizationService
import top.chiloven.lukosbot2.core.auth.BotAdminService
import top.chiloven.lukosbot2.core.command.bot.CommandRegistry
import top.chiloven.lukosbot2.core.policy.PolicyService
import top.chiloven.lukosbot2.core.service.ServiceManager
import top.chiloven.lukosbot2.core.state.StateRegistry
import top.chiloven.lukosbot2.core.state.StateService

@Configuration(proxyBeanMethods = false)
class AdminCommandsConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["admin"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun adminCommand(
        botAdmins: BotAdminService,
        authz: AuthorizationService,
    ): AdminCommand = AdminCommand(botAdmins, authz)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["pref"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun prefCommand(
        registry: StateRegistry,
        states: StateService,
        authz: AuthorizationService,
    ): PrefCommand = PrefCommand(registry, states, authz)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["service"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun serviceCommand(
        services: ServiceManager,
        authz: AuthorizationService,
    ): ServiceCommand = ServiceCommand(services, authz)

    @Bean
    fun helpCommand(
        registryProvider: ObjectProvider<CommandRegistry>,
        appProperties: AppProperties,
        policyService: PolicyService,
    ): HelpCommand = HelpCommand(
        registryProvider = { registryProvider.`object` },
        appProperties = appProperties,
        policyService = policyService,
    )

}
