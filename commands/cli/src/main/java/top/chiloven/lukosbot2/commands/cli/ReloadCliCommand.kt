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

import kotlinx.coroutines.CancellationException
import top.chiloven.lukosbot2.commands.ICliCommand
import top.chiloven.lukosbot2.core.IReloadControl
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.dsl.cliCommand

class ReloadCliCommand(
    private val reloadManager: IReloadControl
) : ICliCommand {

    override fun definition() = cliCommand("reload") {
        alias("rl")
        description = "Reload the whole bot or one/more modules."

        argv {
            positional("modules", ArgType.StringType) {
                required = false
                greedy = true
            }
            execute { args ->
                val raw = args.getOrNull<String>("modules")
                if (raw.isNullOrBlank()) {
                    try {
                        reloadManager.reloadWholeBot()
                        source.println("Reloaded whole bot.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        source.printlnErr("Reload failed: ${e.message}", e)
                    }
                } else {
                    val modules = raw.split(Regex("[,\\s]+"))
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                    try {
                        val r = reloadManager.reloadModules(modules)
                        source.println("Reloaded: ${r.joinToString(", ")}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        source.printlnErr("Reload failed: ${e.message}", e)
                    }
                }
            }
        }
    }

}
