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
import top.chiloven.lukosbot2.commands.cli.ReloadCliCommand
import top.chiloven.lukosbot2.commands.cli.SendCliCommand
import top.chiloven.lukosbot2.commands.cli.ShutdownCliCommand
import top.chiloven.lukosbot2.core.IApplicationControl
import top.chiloven.lukosbot2.core.IReloadControl
import top.chiloven.lukosbot2.core.MessageSenderHub

@Configuration(proxyBeanMethods = false)
class CliCommandsConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.cli.control",
        name = ["reload"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun reloadCliCommand(reloadManager: IReloadControl): ReloadCliCommand =
        ReloadCliCommand(reloadManager)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.cli.control",
        name = ["send"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun sendCliCommand(msh: MessageSenderHub): SendCliCommand =
        SendCliCommand(msh)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.cli.control",
        name = ["shutdown"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun shutdownCliCommand(appControl: IApplicationControl): ShutdownCliCommand =
        ShutdownCliCommand(appControl)

}
