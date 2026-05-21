plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    api(platform(libs.spring.boot.dependencies.bom))
    api(libs.spring.boot.starter)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
