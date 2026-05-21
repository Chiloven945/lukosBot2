import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.lombok) apply false
    alias(libs.plugins.spring.boot) apply false
}

group = property("group") as String
version = property("version") as String
description = property("description") as String

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

