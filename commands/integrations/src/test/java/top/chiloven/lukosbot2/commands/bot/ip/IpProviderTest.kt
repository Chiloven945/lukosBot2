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

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import top.chiloven.lukosbot2.commands.bot.ip.provider.impl.IpQueryIoProvider
import top.chiloven.lukosbot2.commands.bot.ip.provider.impl.IpSbProvider
import kotlin.test.assertEquals

class IpProviderTest {

    @Test
    fun `IpSbProvider parses geoip response`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/geoip/1.1.1.1", request.url.encodedPath)
                    respond(
                        content = """
                            {
                              "ip": "1.1.1.1",
                              "country": "Australia",
                              "country_code": "AU",
                              "region": "Queensland",
                              "city": "Brisbane",
                              "asn": "13335",
                              "organization": "Cloudflare"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val provider = IpSbProvider(client)
        val data = provider.query("1.1.1.1")

        assertEquals("1.1.1.1", data.ip)
        assertEquals("Australia", data.country)
        assertEquals("AU", data.countryCode)
        assertEquals("Queensland", data.region)
        assertEquals("Brisbane", data.city)
        assertEquals("13335", data.asn)
        assertEquals("Cloudflare", data.org)
    }

    @Test
    fun `IpQueryIoProvider parses ip response with risk`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/1.1.1.1", request.url.encodedPath)
                    respond(
                        content = """
                            {
                              "ip": "1.1.1.1",
                              "isp": {
                                "asn": "AS13335",
                                "org": "Cloudflare, Inc.",
                                "isp": "Cloudflare"
                              },
                              "location": {
                                "country": "United States",
                                "country_code": "US",
                                "state": "California",
                                "city": "San Francisco",
                                "zipcode": "94107",
                                "timezone": "America/Los_Angeles"
                              },
                              "risk": {
                                "is_mobile": false,
                                "is_vpn": false,
                                "is_tor": false,
                                "is_proxy": false,
                                "is_datacenter": true,
                                "risk_score": 0
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val provider = IpQueryIoProvider(client)
        val data = provider.query("1.1.1.1")

        assertEquals("1.1.1.1", data.ip)
        assertEquals("United States", data.country)
        assertEquals("US", data.countryCode)
        assertEquals("California", data.region)
        assertEquals("San Francisco", data.city)
        assertEquals("94107", data.postalCode)
        assertEquals("Cloudflare", data.isp)
        assertEquals(true, data.risk?.isDatacenter)
        assertEquals(0, data.risk?.riskScore)
    }

}
