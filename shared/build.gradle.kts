plugins {
    alias(libs.plugins.kotlin.jvm)
}

val javaVersion = providers.gradleProperty("javaVersion").get().toInt()

kotlin {
    jvmToolchain(javaVersion)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

configurations.configureEach {
    exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "ch.qos.logback", module = "logback-core")
}


dependencies {
    implementation(libs.jackson.core)
    implementation(libs.jackson.core.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.log4j.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.zip4j)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
