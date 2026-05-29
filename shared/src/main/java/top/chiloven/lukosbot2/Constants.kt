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
package top.chiloven.lukosbot2

import java.util.*

/**
 * Singleton object for holding application-wide constants.
 */
object Constants {

    @JvmField
    val VERSION: String = BuildInfo.VERSION

    const val APP_NAME = "lukosBot2"

    @JvmField
    val UA: String = "$APP_NAME/$VERSION"

    val javaVersion: String = "%s (%s)".format(
        System.getProperty("java.version"),
        System.getProperty("java.vendor.version")
    )
    val kotlinVersion: String = KotlinVersion.CURRENT.toString()
    val springBootVersion: String = getImplVersion("org.springframework.boot.SpringApplication")

    val tgVersion: String = getMavenVersion("org.telegram", "telegrambots-client")
    val jdaVersion: String = getImplVersion("net.dv8tion.jda.api.JDA")

    private fun getImplVersion(className: String): String =
        runCatching { Class.forName(className).`package`?.implementationVersion }
            .getOrNull() ?: "unknown"

    @Suppress("SameParameterValue")
    private fun getMavenVersion(groupId: String, artifactId: String): String {
        val path = "META-INF/maven/$groupId/$artifactId/pom.properties"

        return runCatching {
            Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.use { input ->
                Properties().apply { load(input) }.getProperty("version")
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    }

}
