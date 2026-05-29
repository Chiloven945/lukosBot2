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
package top.chiloven.lukosbot2.commands.cli

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.ICliCommand
import top.chiloven.lukosbot2.core.IApplicationControl
import top.chiloven.lukosbot2.core.command.definition.dsl.cliCommand

@Service
@ConditionalOnProperty(
    prefix = "lukos.cli.control",
    name = ["shutdown"],
    havingValue = "true",
    matchIfMissing = true
)
class ShutdownCliCommand(
    private val appControl: IApplicationControl
) : ICliCommand {

    override fun definition() = cliCommand("shutdown") {
        alias("stop", "close")
        description = "Shutdown the bot process"

        execute {
            Thread.ofVirtual()
                .name("shutdown-trigger")
                .start { appControl.shutdown() }
        }
    }

}
