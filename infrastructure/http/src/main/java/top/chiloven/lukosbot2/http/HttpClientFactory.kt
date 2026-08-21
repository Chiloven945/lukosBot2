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
package top.chiloven.lukosbot2.http

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.config.ProxyConfigProp

object HttpClientFactory {

    @JvmStatic
    fun createHttpClient(
        proxyConfig: ProxyConfigProp? = null,
        configure: HttpClientConfig<OkHttpConfig>.() -> Unit = {}
    ): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    if (proxyConfig?.enabled == true) {
                        proxyConfig.applyTo(this)
                    }
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (compatible; ${Constants.UA})")
            }
            expectSuccess = false
            configure()
        }
    }

}

typealias HttpClientConfiguration = HttpClientFactory
