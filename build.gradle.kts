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
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.lombok) apply false
    alias(libs.plugins.spring.boot) apply false
}

group = property("group") as String

val baseVersion = property("version") as String
version = baseVersion

description = property("description") as String

fun gitShortHashProvider(): Provider<String> {
    val githubShaProvider = providers.environmentVariable("GITHUB_SHA")
        .map { it.trim().take(8) }

    val localGitProvider = providers.provider {
        runCatching {
            providers.exec {
                commandLine("git", "rev-parse", "--short=8", "HEAD")
            }.standardOutput.asText.get().trim()
        }.getOrDefault("unknown")
    }

    return githubShaProvider.orElse(localGitProvider)
}

val devBuildProvider: Provider<Boolean> = providers.gradleProperty("devBuild")
    .map {
        it.toBooleanStrictOrNull()
            ?: error("Gradle property devBuild must be true or false, but was: $it")
    }
    .orElse(providers.provider {
        val taskNames = gradle.startParameter.taskNames
        taskNames.none { it.endsWith("releaseBootJar") }
    })

val gitShortHash = gitShortHashProvider()

val lukosDisplayVersionProvider: Provider<String> = providers.provider {
    if (devBuildProvider.get()) {
        "$baseVersion+${gitShortHash.get()}-Build"
    } else {
        baseVersion
    }
}

extra["lukosDisplayVersionProvider"] = lukosDisplayVersionProvider

val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()

subprojects {
    group = rootProject.group
    version = rootProject.version
    description = rootProject.description

    configurations.configureEach {
        exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
    }

    configurations.matching { it.name == "implementation" }.configureEach {
        project.dependencies.addProvider(this.name, rootProject.libs.jspecify)
        project.dependencies.addProvider(this.name, rootProject.libs.log4j.api)
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.spring") {
        configurations.matching { it.name == "implementation" }.configureEach {
            val springBootBom = project.providers.provider {
                project.dependencies.platform(rootProject.libs.spring.boot.dependencies.bom.get())
            }
            project.dependencies.addProvider(this.name, springBootBom)
            project.dependencies.addProvider(this.name, rootProject.libs.spring.boot.starter.asProvider())
        }
    }

    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(javaVersion))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(javaVersion)
            options.encoding = "UTF-8"
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(javaVersion)
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                freeCompilerArgs.add("-Xannotation-default-target=param-property")
            }
        }
    }
}
