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

import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.core.command.definition.ArgType
import top.chiloven.lukosbot2.core.command.definition.ValueValidator
import top.chiloven.lukosbot2.core.command.definition.dsl.botCommand
import top.chiloven.lukosbot2.util.MathUtils
import java.math.BigInteger

class DiceCommand : IBotCommand {

    private val commandDefinition = botCommand("dice") {
        description = "掷骰子，可指定骰子数量"

        argv {
            positional("count", ArgType.LongType) {
                required = false
                default = 1L
                description = "骰子数量"
                validator = ValueValidator { value ->
                    val v = value as? Long ?: return@ValueValidator "骰子数量必须是正整数。"
                    if (v <= 0L) "骰子数量必须是正整数。" else null
                }
            }

            execute { args ->
                val count = args.get<Long>("count")
                source.reply(runDice(count))
            }
        }

        example(
            "dice",
            "dice 3"
        )
    }

    override fun definition() = commandDefinition

    private fun runDice(count: Long): String {
        if (count <= 0) return "骰子数量必须是正整数。"

        val faces = MathUtils.approximateMultinomial(
            count,
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0
        )

        if (count == 1L) {
            val face = faces.indexOfFirst { it > 0 } + 1

            return """
            你掷了 1 个骰子。
            朝上的一面是……$face！
            """.trimIndent()
        }

        val sum = faces.indices.fold(BigInteger.ZERO) { acc, i ->
            acc + faces[i].toBigInteger() * (i + 1).toBigInteger()
        }

        return """
        你掷了 $count 个骰子。
        其中，点数为 1 的有 ${faces[0]} 个，2 的有 ${faces[1]} 个，3 的有 ${faces[2]} 个，4 的有 ${faces[3]} 个，5 的有 ${faces[4]} 个，6 的有 ${faces[5]} 个。
        它们的点数合计为 $sum！
        """.trimIndent()
    }

}
