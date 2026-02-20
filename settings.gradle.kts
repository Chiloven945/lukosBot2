@file:Suppress("LocalVariableName")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        val springBootVersion = extra["spring-boot-version"].toString()
        val kotlinVersion = extra["kotlin-version"].toString()

        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version "1.1.7"

        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.lombok") version kotlinVersion
    }
}

rootProject.name = "lukosBot2"
