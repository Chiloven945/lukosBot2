@file:Suppress("LocalVariableName")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        val spring_boot_version = (settings.extra["spring-boot-version"] as String?)
            ?: error("Missing spring-boot-version in gradle.properties")

        id("org.springframework.boot") version spring_boot_version
        id("io.spring.dependency-management") version "1.1.7"
    }
}

rootProject.name = "lukosBot2"
