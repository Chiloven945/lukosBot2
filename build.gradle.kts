plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.lombok)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

val javaVersionInt = libs.versions.java.get().toInt()

group = property("group") as String
version = property("version") as String
description = property("description") as String

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersionInt))
    }
}

dependencies {
    // Platform API
    implementation(libs.jda)
    implementation(libs.shiro)
    implementation(libs.telegrambots.longpolling)
    implementation(libs.telegrambots.client)

    // Spring Boot
    annotationProcessor(libs.spring.boot.configuration.processor)
    implementation(libs.spring.boot.starter) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
    implementation(libs.spring.boot.starter.log4j2)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    runtimeOnly(libs.h2)

    // Core Library
    implementation(libs.brigadier)
    implementation(libs.commons.net)
    implementation(libs.gson)
    implementation(libs.jackson.core.v3)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.snakeyaml)
    implementation(libs.okhttp)
    implementation(libs.zip4j)

    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j2.impl)

    implementation(libs.docker.java)
    implementation(libs.docker.java.transport.httpclient5)

    // Feature Library
    implementation(libs.flexmark.html2md.converter)
    implementation(libs.selenium.java)
    implementation(libs.jsoup)
    implementation(libs.webdrivermanager)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersionInt)
}

tasks.test {
    failOnNoDiscoveredTests = false
}

springBoot {
    mainClass.set("top.chiloven.lukosbot2.Main")
}

configurations.all {
    exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")

    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "ch.qos.logback", module = "logback-core")
}

kotlin {
    jvmToolchain(javaVersionInt)
}
