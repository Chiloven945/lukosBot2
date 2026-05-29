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

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "lukos")
data class AppProperties(
    /**
     * The prefix to trigger command processing, default is "/".
     */
    var prefix: String = "/",

    /**
     * The language for responses, default is "zh-cn".
     * Supported languages: en-us, zh-cn, zh-tw, ja-jp.
     */
    var language: String = "zh-cn",

    var telegram: Telegram = Telegram(),
    var discord: Discord = Discord(),
    var cli: Cli = Cli(),
    var image: Image = Image(),
    var security: Security = Security(),
    var policy: Policy = Policy(),
) {

    data class Telegram(
        /**
         * Whether to enable the Telegram bot platform, default is false.
         */
        var enabled: Boolean = false,

        /**
         * The bot token provided by BotFather, default is empty.
         */
        var botToken: String = "",

        /**
         * The bot username, can be empty to let the library fetch it, default is empty.
         */
        var botUsername: String = "",
    )

    data class Discord(
        /**
         * Whether to enable the Discord platform, default is false.
         */
        var enabled: Boolean = false,

        /**
         * The bot token provided by Discord Developer Portal, default is empty.
         */
        var token: String = "",
    )

    data class Cli(
        /**
         * Whether to enable the Command Line Interface feature to control the bot.
         */
        var enabled: Boolean = true,
    )

    data class Image(
        /**
         * Theme for ModernImageDraw-based generated images.
         */
        var theme: String = "light",
    )

    data class Policy(
        /**
         * Runtime policy rules evaluated against the current chat context.
         */
        var rules: MutableList<Rule> = ArrayList(),
    ) {

        data class Rule(
            var id: String = "",
            var priority: Int = 0,
            var `when`: Match = Match(),
            var disableCommands: MutableList<String> = ArrayList(),
            var allowValues: MutableMap<String, MutableList<String>> = LinkedHashMap(),
        )

        data class Match(
            var platform: String? = null,
            var privateChat: Boolean? = null,
            var group: Boolean? = null,
            var nsfw: Boolean? = null,
            var chatId: Long? = null,
            var userId: Long? = null,
        )

    }

    data class Security(
        /**
         * Bootstrap bot admins defined in config.
         */
        var bootstrapBotAdmins: MutableMap<String, MutableList<Long>> = LinkedHashMap(),
    )

}
