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
import top.chiloven.lukosbot2.core.MessageSenderHub
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.cliCommand
import top.chiloven.lukosbot2.core.model.message.Address.parse
import top.chiloven.lukosbot2.core.model.message.outbound.OutboundMessage.text

@Service
@ConditionalOnProperty(
    prefix = "lukos.cli.control",
    name = ["send"],
    havingValue = "true",
    matchIfMissing = true
)
class SendCliCommand(
    val msh: MessageSenderHub
) : ICliCommand {

    override fun definition() = cliCommand("send") {
        alias("text", "msg")
        description = "Send a message to a chat with platform and chat ID."

        argv {
            positional("target", ArgType.StringType) {
                required = true
            }
            positional("text", ArgType.StringType) {
                required = true
                greedy = true
            }
            execute { args ->
                try {
                    val addr = parse(args.get("target"))
                    msh.send(text(addr, args.get("text")))

                    source.println("Successfully sent a message to $addr.")
                } catch (e: Exception) {
                    source.printlnErr("Failed to send: ${e.message}", e)
                }
            }
        }
    }

}
