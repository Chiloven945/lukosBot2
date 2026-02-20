plugins {
    java
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.3.10"
    kotlin("plugin.lombok") version "1.9.20"
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val javaVersionInt = (property("java-version") as String).toInt()

group = property("group") as String
version = property("version") as String
description = "lukosBot2"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersionInt))
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://libraries.minecraft.net")
}

dependencies {
    // Platform API
    implementation("net.dv8tion:JDA:${property("jda-version")}")
    implementation("com.mikuac:shiro:${property("shiro-version")}")
    implementation("org.telegram:telegrambots-longpolling:${property("telegrambots-version")}")
    implementation("org.telegram:telegrambots-client:${property("telegrambots-version")}")

    // Spring Boot
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${property("spring-boot-version")}")
    implementation("org.springframework.boot:spring-boot-starter:${property("spring-boot-version")}") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
    implementation("org.springframework.boot:spring-boot-starter-log4j2:${property("spring-boot-version")}")
    implementation("org.springframework.boot:spring-boot-starter-actuator:${property("spring-boot-version")}")

    // Core Library
    implementation("com.mojang:brigadier:1.3.10")
    implementation("commons-net:commons-net:3.12.0")
    implementation("com.google.code.gson:gson:${property("gson-version")}")
    compileOnly("org.projectlombok:lombok:${property("lombok-version")}")
    annotationProcessor("org.projectlombok:lombok:${property("lombok-version")}")
    implementation("org.yaml:snakeyaml:2.5")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    implementation("org.apache.logging.log4j:log4j-api:${property("log4j2-version")}")
    implementation("org.apache.logging.log4j:log4j-core:${property("log4j2-version")}")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:${property("log4j2-version")}")

    implementation("com.github.docker-java:docker-java:${property("docker-java-version")}")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:${property("docker-java-version")}")

    // Feature Library
    implementation("com.vladsch.flexmark:flexmark-html2md-converter:0.64.8")
    implementation("org.seleniumhq.selenium:selenium-java:${property("selenium-version")}")
    implementation("org.jsoup:jsoup:${property("jsoup-version")}")
    implementation("io.github.bonigarcia:webdrivermanager:${property("webdrivermanager-version")}")

    implementation(kotlin("stdlib"))
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
    jvmToolchain(25)
}
