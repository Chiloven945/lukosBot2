plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    api(project(":shared"))
    api(project(":infrastructure:spring"))

    api(platform(libs.spring.boot.dependencies.bom))
    api(libs.spring.boot.starter)

    compileOnly(libs.lombok)
    annotationProcessor(platform(libs.spring.boot.dependencies.bom))
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
