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
import top.chiloven.lukosbot2.commands.bot.wikis.McWikiCommand
import top.chiloven.lukosbot2.commands.bot.wikis.WikiCommand
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.util.feature.WebScreenshot
import top.chiloven.lukosbot2.util.feature.WebToMarkdown

@Configuration(proxyBeanMethods = false)
class WebCommandsConfiguration {

    @Bean
    fun webToMarkdown(proxyConfig: ProxyConfigProp): WebToMarkdown =
        WebToMarkdown(proxyConfig)

    @Bean
    fun webScreenshot(proxyConfig: ProxyConfigProp): WebScreenshot =
        WebScreenshot(proxyConfig)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["wiki"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun wikiCommand(
        webScreenshot: WebScreenshot,
        webToMarkdown: WebToMarkdown,
    ): WikiCommand = WikiCommand(webScreenshot, webToMarkdown)

    @Bean
    @ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = ["mcwiki"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun mcWikiCommand(
        webScreenshot: WebScreenshot,
        webToMarkdown: WebToMarkdown,
    ): McWikiCommand = McWikiCommand(webScreenshot, webToMarkdown)

}
