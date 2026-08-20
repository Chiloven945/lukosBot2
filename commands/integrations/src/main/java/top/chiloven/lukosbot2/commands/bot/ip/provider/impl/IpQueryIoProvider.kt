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
class IpQueryIoProvider(
    private val http: HttpClient
) : IIpProvider {

    private data class IpQueryIoResponse(
        val ip: String? = null,
        val isp: IspDto? = null,
        val location: LocationDto? = null,
        val risk: RiskDto? = null
    ) {

        data class IspDto(
            val asn: String? = null,
            val org: String? = null,
            val isp: String? = null
        )

        data class LocationDto(
            val country: String? = null,
            val countryCode: String? = null,
            val state: String? = null,
            val city: String? = null,
            val zipcode: String? = null,
            val latitude: Double? = null,
            val longitude: Double? = null,
            val timezone: String? = null
        )

        data class RiskDto(
            val isMobile: Boolean? = null,
            val isVpn: Boolean? = null,
            val isTor: Boolean? = null,
            val isProxy: Boolean? = null,
            val isDatacenter: Boolean? = null,
            val riskScore: Int? = null
        )

    }

    private companion object {

        private const val BASE = "https://api.ipquery.io"

    }

    override fun id(): String = "ipquery"

    override fun aliases(): Set<String> = setOf("ipquery.io", "ip-query")

    override fun priority(): Int = 100

    override suspend fun query(ip: String): IpData {
        val text = http.get("$BASE/$ip").requireSuccess().bodyAsText()
        val dto = JsonUtils.SNAKE_CASE_MAPPER.readValue(
            text,
            IpQueryIoResponse::class.java
        )

        return IpData(
            ip = dto.ip ?: ip,
            country = dto.location?.country,
            countryCode = dto.location?.countryCode,
            region = dto.location?.state,
            city = dto.location?.city,
            postalCode = dto.location?.zipcode,
            latitude = dto.location?.latitude,
            longitude = dto.location?.longitude,
            timezone = dto.location?.timezone,
            asn = dto.isp?.asn,
            org = dto.isp?.org,
            isp = dto.isp?.isp,
            risk = dto.risk?.let {
                IpData.IpRisk(
                    isMobile = it.isMobile,
                    isVpn = it.isVpn,
                    isTor = it.isTor,
                    isProxy = it.isProxy,
                    isDatacenter = it.isDatacenter,
                    riskScore = it.riskScore
                )
            }
        )
    }

}
