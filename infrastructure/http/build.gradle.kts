plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    api(platform(libs.spring.boot.dependencies.bom))
    api(project(":shared"))
    api(project(":infrastructure:spring"))
    api(libs.jackson.core.databind)
    api(libs.okhttp)
    api(libs.spring.boot.starter)

    implementation(libs.kotlinx.coroutines.core)

    compileOnly(libs.lombok)
    annotationProcessor(platform(libs.spring.boot.dependencies.bom))
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
