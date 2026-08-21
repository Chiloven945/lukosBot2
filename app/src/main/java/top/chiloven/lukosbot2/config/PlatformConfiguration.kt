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

import io.ktor.client.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import top.chiloven.lukosbot2.core.auth.impl.DiscordChatAdminResolver
import top.chiloven.lukosbot2.core.auth.impl.TelegramChatAdminResolver
import top.chiloven.lukosbot2.platform.telegram.TelegramFileLoader

@Configuration(proxyBeanMethods = false)
class PlatformConfiguration {

    @Bean
    fun telegramFileLoader(props: AppProperties, http: HttpClient): TelegramFileLoader =
        TelegramFileLoader(props, http)

    @Bean
    fun telegramChatAdminResolver(props: AppProperties): TelegramChatAdminResolver =
        TelegramChatAdminResolver(props)

    @Bean
    fun discordChatAdminResolver(): DiscordChatAdminResolver =
        DiscordChatAdminResolver()

}
