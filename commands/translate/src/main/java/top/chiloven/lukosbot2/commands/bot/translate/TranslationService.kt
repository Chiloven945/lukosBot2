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
package top.chiloven.lukosbot2.commands.bot.translate

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import top.chiloven.lukosbot2.config.CommandConfigProp.Translate
import top.chiloven.lukosbot2.http.requireSuccess
import top.chiloven.lukosbot2.util.HttpStatusException
import top.chiloven.lukosbot2.util.JsonUtils
import java.io.IOException
import java.time.Duration

class TranslationService(
    translate: Translate,
    private val http: HttpClient
) {

    private companion object {

        private const val IMAGE_NAME = "libretranslate/libretranslate:latest"
        private const val CONTAINER_NAME = "lukos-libretranslate"
        private const val CONTAINER_PORT = 5000
        private const val HOST_PORT = 5000

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class LibreTranslateResponse(
        val translatedText: String? = null,
        val error: String? = null
    )

    private val defaultLang: String = translate.defaultLang.takeIf { it.isNotBlank() } ?: "en"
    private val baseUrl: String
    private val dockerClient: DockerClient?

    init {
        if (translate.url.isNotBlank()) {
            this.baseUrl = normalizeBaseUrl(translate.url)
            this.dockerClient = null
        } else {
            this.baseUrl = "http://127.0.0.1:$HOST_PORT"
            this.dockerClient = createDockerClient()
            ensureLibreTranslateContainer()
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        val u = url.trim()
        return if (u.endsWith("/")) u.substring(0, u.length - 1) else u
    }

    private fun createDockerClient(): DockerClient {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

        val dockerHttpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
                .dockerHost(config.dockerHost)
                .sslConfig(config.sslConfig)
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(30))
                .build()

        return DockerClientImpl.getInstance(config, dockerHttpClient)
    }

    /* ===================== Docker 部分 ===================== */

    private fun ensureLibreTranslateContainer() {
        val docker = dockerClient ?: return
        try {
            val containers: List<Container> = docker.listContainersCmd()
                    .withShowAll(true)
                    .exec()

            val existing = containers.firstOrNull { c ->
                val names = c.names
                names != null && names.contains("/$CONTAINER_NAME")
            }

            val containerId: String
            if (existing == null) {
                docker.pullImageCmd(IMAGE_NAME).start().awaitCompletion()

                val portBindings = Ports().apply {
                    bind(
                        ExposedPort.tcp(CONTAINER_PORT),
                        Ports.Binding.bindPort(HOST_PORT)
                    )
                }

                val hostConfig = HostConfig.newHostConfig()
                        .withPortBindings(portBindings)

                val created = docker.createContainerCmd(IMAGE_NAME)
                        .withName(CONTAINER_NAME)
                        .withHostConfig(hostConfig)
                        .exec()

                containerId = created.id
            } else {
                containerId = existing.id
            }

            val inspect = docker.inspectContainerCmd(containerId).exec()
            if (inspect.state.running != true) {
                docker.startContainerCmd(containerId).exec()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("启动 LibreTranslate 容器时被中断", e)
        } catch (e: DockerException) {
            throw IllegalStateException(
                "确保 LibreTranslate 容器运行失败，请确认 Docker 已安装并正在运行",
                e
            )
        }
    }

    /**
     * 对外翻译接口
     */
    suspend fun translate(
        from: String?,
        to: String?,
        text: String
    ): String {
        val src = if (from.isNullOrBlank()) "auto" else from
        val tgt = if (to.isNullOrBlank()) defaultLang else to

        return try {
            val response = http.submitForm(
                url = "$baseUrl/translate",
                formParameters = parameters {
                    append("q", text)
                    append("source", src)
                    append("target", tgt)
                    append("format", "text")
                }
            ) {
                header(HttpHeaders.Accept, "application/json")
                timeout {
                    requestTimeoutMillis = 30_000
                }
            }.requireSuccess()

            val responseBody = response.bodyAsText()
            extractTranslatedText(responseBody)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpStatusException) {
            "翻译失败（HTTP ${e.statusCode}）：${e.responseBodySnippet.orEmpty()}"
        } catch (e: IOException) {
            "翻译失败：${e.message}"
        } catch (e: Exception) {
            "翻译失败：${e.message ?: "未知错误"}"
        }
    }

    private fun extractTranslatedText(body: String): String {
        return try {
            val dto = JsonUtils.MAPPER.readValue(body, LibreTranslateResponse::class.java)
            if (!dto.translatedText.isNullOrBlank()) {
                return dto.translatedText
            }
            if (!dto.error.isNullOrBlank()) {
                return "翻译失败：${dto.error}"
            }
            "翻译结果缺失：$body"
        } catch (_: Exception) {
            "解析翻译结果失败：$body"
        }
    }

}
