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

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import top.chiloven.lukosbot2.commands.bot.HelpCommand
import top.chiloven.lukosbot2.core.*
import top.chiloven.lukosbot2.core.auth.AuthorizationService
import top.chiloven.lukosbot2.core.auth.BotAdminService
import top.chiloven.lukosbot2.core.command.bot.CommandProcessor
import top.chiloven.lukosbot2.core.command.bot.CommandRegistry
import top.chiloven.lukosbot2.core.command.cli.CliCmdProcessor
import top.chiloven.lukosbot2.core.command.cli.CliCmdRegistry
import top.chiloven.lukosbot2.core.policy.PolicyService
import top.chiloven.lukosbot2.core.service.ServiceManager
import top.chiloven.lukosbot2.core.service.ServiceRegistry
import top.chiloven.lukosbot2.core.state.StateRegistry
import top.chiloven.lukosbot2.core.state.StateService
import top.chiloven.lukosbot2.core.state.store.IStateStore
import top.chiloven.lukosbot2.lifecycle.platform.IPlatformAdapter

@SpringBootTest(
    properties = [
        "lukos.telegram.enabled=false",
        "lukos.discord.enabled=false",
        "lukos.cli.enabled=false",
    ]
)
@Import(ApplicationContextWiringTest.TestConfig::class)
class ApplicationContextWiringTest {

    @TestConfiguration
    class TestConfig {

        @Bean
        fun testPlatformAdapter(): IPlatformAdapter = object : IPlatformAdapter {
            private var running = false
            override fun name(): String = "test"
            override fun start() {
                running = true
            }

            override fun stop() {
                running = false
            }

            override fun isRunning(): Boolean = running
        }
    }

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun contextLoadsAndWiresCoreBeans() {
        assertNotNull(context.getBean(BotCoroutineRuntime::class.java))
        assertNotNull(context.getBean(MessageSenderHub::class.java))
        assertNotNull(context.getBean(CommandRegistry::class.java))
        assertNotNull(context.getBean(CliCmdRegistry::class.java))
        assertNotNull(context.getBean(CliCmdProcessor::class.java))
        assertNotNull(context.getBean(ServiceRegistry::class.java))
        assertNotNull(context.getBean(StateRegistry::class.java))
        assertNotNull(context.getBean(StateService::class.java))
        assertNotNull(context.getBean(BotAdminService::class.java))
        assertNotNull(context.getBean(AuthorizationService::class.java))
        assertNotNull(context.getBean(PolicyService::class.java))
        assertNotNull(context.getBean(ServiceManager::class.java))
        assertNotNull(context.getBean(CommandProcessor::class.java))
        assertNotNull(context.getBean(PipelineProcessor::class.java))
        assertNotNull(context.getBean(MessageDispatcher::class.java))
        assertNotNull(context.getBean(MediaRefLoader::class.java))
        assertNotNull(context.getBean(IStateStore::class.java))
    }

    @Test
    fun contextWiresProperties() {
        assertNotNull(context.getBean(AppProperties::class.java))
        assertNotNull(context.getBean(CommandConfigProp::class.java))
        assertNotNull(context.getBean(ServiceConfigProp::class.java))
        assertNotNull(context.getBean(ProxyConfigProp::class.java))
    }

    @Test
    fun commandRegistryContainsRegisteredCommands() {
        val registry = context.getBean(CommandRegistry::class.java)
        assertTrue(registry.all().isNotEmpty(), "CommandRegistry should have commands registered")
        assertNotNull(registry.get("ping"), "Ping command should be registered")
        assertNotNull(registry.get("help"), "Help command should be registered")
    }

    @Test
    fun helpCommandResolvesRegistryLazily() {
        val helpCommand = context.getBean(HelpCommand::class.java)
        assertNotNull(helpCommand)
        assertNotNull(helpCommand.definition())
    }

}
