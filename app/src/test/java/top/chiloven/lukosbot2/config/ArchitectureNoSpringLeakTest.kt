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

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.ICliCommand
import top.chiloven.lukosbot2.core.MessageDispatcher
import top.chiloven.lukosbot2.core.MessageSenderHub
import top.chiloven.lukosbot2.core.PipelineProcessor
import top.chiloven.lukosbot2.core.auth.AuthorizationService
import top.chiloven.lukosbot2.core.auth.BotAdminService
import top.chiloven.lukosbot2.core.command.bot.CommandProcessor
import top.chiloven.lukosbot2.core.command.bot.CommandRegistry
import top.chiloven.lukosbot2.core.policy.PolicyService
import top.chiloven.lukosbot2.core.service.ServiceManager
import top.chiloven.lukosbot2.core.service.ServiceRegistry
import top.chiloven.lukosbot2.core.state.StateRegistry
import top.chiloven.lukosbot2.core.state.StateService
import top.chiloven.lukosbot2.core.state.store.JdbcStateStore

class ArchitectureNoSpringLeakTest {

    private val forbiddenAnnotationNames = setOf(
        "org.springframework.stereotype.Component",
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository",
        "org.springframework.stereotype.Controller",
        "org.springframework.context.annotation.Configuration",
        "org.springframework.context.annotation.Bean",
        "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty",
        "jakarta.annotation.PostConstruct",
        "jakarta.annotation.PreDestroy",
    )

    @Test
    fun coreRuntimeAndCommandClassesHaveNoSpringAnnotations() {
        val classesToCheck = listOf(
            MessageDispatcher::class.java,
            MessageSenderHub::class.java,
            PipelineProcessor::class.java,
            AuthorizationService::class.java,
            BotAdminService::class.java,
            CommandProcessor::class.java,
            CommandRegistry::class.java,
            PolicyService::class.java,
            ServiceManager::class.java,
            ServiceRegistry::class.java,
            StateRegistry::class.java,
            StateService::class.java,
            IBotCommand::class.java,
            ICliCommand::class.java,
            JdbcStateStore::class.java,
        )

        for (clazz in classesToCheck) {
            for (annotation in clazz.annotations) {
                val name = annotation.annotationClass.java.name
                assertTrue(
                    name !in forbiddenAnnotationNames,
                    "Class ${clazz.name} should not have Spring/Jakarta annotation $name",
                )
            }
        }
    }

}
