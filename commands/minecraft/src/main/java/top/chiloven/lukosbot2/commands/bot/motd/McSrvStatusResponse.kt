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
package top.chiloven.lukosbot2.commands.bot.motd

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils

@JsonIgnoreProperties(ignoreUnknown = true)
data class McSrvStatusResponse(
    val online: Boolean = false,
    val ip: String? = null,
    val port: Int? = null,
    val hostname: String? = null,
    val version: String? = null,
    val protocol: ProtocolInfo? = null,
    val icon: String? = null,
    val software: String? = null,
    val eulaBlocked: Boolean? = null,
    val motd: MotdInfo? = null,
    val players: PlayersInfo? = null,
    val debug: DebugInfo? = null,
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ProtocolInfo(
        val version: Int? = null,
        val name: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MotdInfo(
        val raw: List<String>? = null,
        val clean: List<String>? = null,
        val html: List<String>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PlayersInfo(
        val online: Int? = null,
        val max: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DebugInfo(
        val ping: Boolean? = null,
        val query: Boolean? = null,
        val bedrock: Boolean? = null,
        val srv: Boolean? = null,
        val cachehit: Boolean? = null,
    )

    companion object {

        fun fromJsonObject(obj: ObjectNode): McSrvStatusResponse =
            JsonUtils.snakeTreeToValue(obj, McSrvStatusResponse::class.java)

    }

}
