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
package top.chiloven.lukosbot2.commands.bot.ip

data class IpQueryResult(
    val providerId: String,
    val data: IpData,
    val failures: List<IpProviderFailure> = emptyList()
) {

    fun toDisplayText(): String = buildString {
        append(data.toDisplayText(providerId))

        if (failures.isNotEmpty()) {
            append("\n\n")
            append("提示：部分数据源暂时不可用，已自动切换到 ")
            append(providerId)
            append("。")
            append("不可用数据源：")
            append(failures.joinToString("、") { it.providerId })
        }
    }

    data class IpProviderFailure(
        val providerId: String,
        val reason: String
    )

    class IpQueryException(
        val ip: String,
        val providerIds: List<String>,
        val failures: List<IpProviderFailure>
    ) : RuntimeException(
        buildString {
            append("IP 查询失败，所有数据源均不可用")
            if (providerIds.isNotEmpty()) {
                append("。已尝试：")
                append(providerIds.joinToString("、"))
            }
            if (failures.isNotEmpty()) {
                append("。失败原因：")
                append(failures.joinToString("；") { "${it.providerId}: ${it.reason}" })
            }
        }
    )

}
