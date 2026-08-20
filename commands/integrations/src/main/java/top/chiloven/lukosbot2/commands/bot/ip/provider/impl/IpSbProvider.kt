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

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.springframework.stereotype.Component
import top.chiloven.lukosbot2.commands.bot.ip.IpData
import top.chiloven.lukosbot2.commands.bot.ip.provider.IIpProvider
import top.chiloven.lukosbot2.http.requireSuccess
import top.chiloven.lukosbot2.util.JsonUtils

@Component
class IpSbProvider(
    private val http: HttpClient
) : IIpProvider {

    private data class IpSbResponse(
        val ip: String? = null,
        val country: String? = null,
        val countryCode: String? = null,
        val region: String? = null,
        val regionCode: String? = null,
        val city: String? = null,
        val postalCode: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val timezone: String? = null,
        val asn: String? = null,
        val organization: String? = null
    )

    private companion object {

        private const val BASE = "https://api.ip.sb"

    }

    override fun id(): String = "ipsb"

    override fun aliases(): Set<String> = setOf("ip.sb", "ip-sb")

    override fun priority(): Int = 80

    override suspend fun query(ip: String): IpData {
        val text = http.get("$BASE/geoip/$ip").requireSuccess().bodyAsText()
        val dto = JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            IpSbResponse::class.java
        )

        return IpData(
            ip = dto.ip ?: ip,
            country = dto.country,
            countryCode = dto.countryCode,
            region = dto.region,
            regionCode = dto.regionCode,
            city = dto.city,
            postalCode = dto.postalCode,
            latitude = dto.latitude,
            longitude = dto.longitude,
            timezone = dto.timezone,
            asn = dto.asn,
            org = dto.organization
        )
    }

}
