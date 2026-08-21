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
package top.chiloven.lukosbot2.config

import io.ktor.client.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import top.chiloven.lukosbot2.core.IUrlMediaLoader
import top.chiloven.lukosbot2.http.HttpClientFactory
import top.chiloven.lukosbot2.http.HttpUrlMediaLoader
import top.chiloven.lukosbot2.util.DownloadClient

@Configuration(proxyBeanMethods = false)
class HttpConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(HttpClient::class)
    fun httpClient(proxyConfig: ProxyConfigProp): HttpClient =
        HttpClientFactory.createHttpClient(proxyConfig)

    @Bean
    @ConditionalOnMissingBean(DownloadClient::class)
    fun downloadClient(proxyConfig: ProxyConfigProp): DownloadClient =
        DownloadClient(proxyConfig)

    @Bean
    @ConditionalOnMissingBean(IUrlMediaLoader::class)
    fun urlMediaLoader(httpClient: HttpClient): IUrlMediaLoader =
        HttpUrlMediaLoader(httpClient)

}
