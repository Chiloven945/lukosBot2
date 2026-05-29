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
package top.chiloven.lukosbot2.commands.bot.ip.provider.impl

import org.springframework.stereotype.Component
import top.chiloven.lukosbot2.commands.bot.ip.IpData
import top.chiloven.lukosbot2.commands.bot.ip.provider.IIpProvider
import top.chiloven.lukosbot2.util.HttpJson
import top.chiloven.lukosbot2.util.JsonUtils.elm
import top.chiloven.lukosbot2.util.JsonUtils.str

@Component
class IpSbProvider : IIpProvider {

    private companion object {

        private const val BASE = "https://api.ip.sb"

    }

    override fun id(): String = "ipsb"

    override fun aliases(): Set<String> = setOf("ip.sb", "ip-sb")

    override fun priority(): Int = 80

    override fun query(ip: String): IpData {
        val obj = HttpJson.getObject("$BASE/geoip/$ip")

        return IpData(
            ip = obj.str("ip") ?: ip,
            country = obj.str("country"),
            countryCode = obj.str("country_code"),
            region = obj.str("region"),
            regionCode = obj.str("region_code"),
            city = obj.str("city"),
            postalCode = obj.str("postal_code"),
            latitude = obj.elm("latitude")?.asDouble(),
            longitude = obj.elm("longitude")?.asDouble(),
            timezone = obj.str("timezone"),
            asn = obj.str("asn"),
            org = obj.str("organization")
        )
    }

}
