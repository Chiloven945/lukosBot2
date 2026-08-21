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
package top.chiloven.lukosbot2.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.ICliCommand
import top.chiloven.lukosbot2.core.*
import top.chiloven.lukosbot2.core.auth.AuthorizationService
import top.chiloven.lukosbot2.core.auth.BotAdminService
import top.chiloven.lukosbot2.core.auth.IChatAdminResolver
import top.chiloven.lukosbot2.core.command.bot.CommandProcessor
import top.chiloven.lukosbot2.core.command.bot.CommandRegistry
import top.chiloven.lukosbot2.core.command.cli.CliCmdProcessor
import top.chiloven.lukosbot2.core.command.cli.CliCmdRegistry
import top.chiloven.lukosbot2.core.policy.PolicyService
import top.chiloven.lukosbot2.core.service.ServiceManager
import top.chiloven.lukosbot2.core.service.ServiceRegistry
import top.chiloven.lukosbot2.core.state.StateRegistry
import top.chiloven.lukosbot2.core.state.StateService
import top.chiloven.lukosbot2.core.state.definition.IStateDefinition
import top.chiloven.lukosbot2.core.state.store.IStateStore
import top.chiloven.lukosbot2.platform.PlatformFileLoader
import top.chiloven.lukosbot2.services.IBotService

@Configuration(proxyBeanMethods = false)
class CoreConfiguration {

    @Bean(destroyMethod = "destroy")
    fun botCoroutineRuntime(): BotCoroutineRuntime = BotCoroutineRuntime()

    @Bean(destroyMethod = "destroy")
    fun messageSenderHub(runtime: BotCoroutineRuntime): MessageSenderHub = MessageSenderHub(runtime)

    @Bean
    fun commandRegistry(commands: List<IBotCommand>): CommandRegistry = CommandRegistry(commands)

    @Bean
    fun cliCmdRegistry(cliCommands: List<ICliCommand>): CliCmdRegistry = CliCmdRegistry(cliCommands)

    @Bean
    fun cliCmdProcessor(registry: CliCmdRegistry): CliCmdProcessor = CliCmdProcessor(registry)

    @Bean
    fun serviceRegistry(services: List<IBotService>): ServiceRegistry = ServiceRegistry(services)

    @Bean
    fun stateRegistry(definitions: List<IStateDefinition<*>>): StateRegistry =
        StateRegistry(definitions)

    @Bean
    fun stateService(store: IStateStore): StateService = StateService(store)

    @Bean
    fun botAdminService(store: IStateStore, props: AppProperties): BotAdminService =
        BotAdminService(store, props)

    @Bean
    fun authorizationService(
        botAdminService: BotAdminService,
        resolvers: List<IChatAdminResolver>,
    ): AuthorizationService = AuthorizationService(botAdminService, resolvers)

    @Bean
    fun policyService(props: AppProperties): PolicyService = PolicyService(props)

    @Bean(initMethod = "init", destroyMethod = "destroy")
    fun serviceManager(
        registry: ServiceRegistry,
        store: IStateStore,
        senderHub: MessageSenderHub,
        props: ServiceConfigProp,
        runtime: BotCoroutineRuntime,
    ): ServiceManager = ServiceManager(registry, store, senderHub, props, runtime)

    @Bean
    fun commandProcessor(
        commands: List<IBotCommand>,
        props: AppProperties,
        registry: CommandRegistry,
        policyService: PolicyService,
    ): CommandProcessor = CommandProcessor(commands, props, registry, policyService)

    @Bean
    fun pipelineProcessor(processors: List<IProcessor>): PipelineProcessor =
        PipelineProcessor(processors)

    @Bean
    fun messageDispatcher(
        senderHub: MessageSenderHub,
        pipeline: PipelineProcessor,
        services: ServiceManager,
        runtime: BotCoroutineRuntime,
    ): MessageDispatcher = MessageDispatcher(senderHub, pipeline, services, runtime)

    @Bean
    fun mediaRefLoader(
        platformFileLoaders: List<PlatformFileLoader>,
        urlMediaLoader: IUrlMediaLoader,
    ): MediaRefLoader = MediaRefLoader(platformFileLoaders, urlMediaLoader)

}
