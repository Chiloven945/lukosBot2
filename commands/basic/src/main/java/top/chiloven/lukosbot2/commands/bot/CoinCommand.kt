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
package top.chiloven.lukosbot2.commands.bot

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.ValueValidator
import top.chiloven.lukosbot2.core.command.definition.dsl.arg
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.core.command.definition.dsl.opt
import top.chiloven.lukosbot2.util.MathUtils

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["coin"],
    havingValue = "true",
    matchIfMissing = true
)
class CoinCommand : IBotCommand {

    private val commandDefinition = botCommand("coin") {
        description = "抛硬币"

        argv {
            positional("count", ArgType.LongType) {
                required = false
                default = 1L
                description = "硬币数量"
                validator = ValueValidator { v ->
                    val n = v as? Long ?: return@ValueValidator "硬币数量必须是正整数。"
                    if (n <= 0L) "硬币数量必须是正整数。" else null
                }
            }
            execute { args -> source.reply(runCoin(args.get("count"))) }
        }

        syntax("抛硬币（默认 1 个）", opt(arg("count")))
        param("count", "硬币数量（正整数）")

        example("coin 10")
    }

    override fun definition() = commandDefinition

    private fun runCoin(times: Long): String {
        if (times <= 0) return "硬币数量必须是正整数。"
        return try {
            val r = MathUtils.approximateMultinomial(
                times,
                0.499999999999,
                0.499999999999,
                0.000000000002
            )

            if (times == 1L) {
                "你抛了 1 个硬币。\n" + when {
                    r[0] == 1L -> "是正面。"
                    r[1] == 1L -> "是反面。"
                    else -> "它立起来了！"
                }
            } else {
                """
                你抛了 %d 个硬币。
                其中 %d 个是正面，%d 个是反面……
                还有 %d 个立起来了！
                """.trimIndent().format(times, r[0], r[1], r[2])
            }
        } catch (e: IllegalArgumentException) {
            "抛硬币失败：${e.message}"
        }
    }

}
