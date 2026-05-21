plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    api(libs.jackson.core)
    api(libs.jackson.core.databind)
    api(libs.jackson.module.kotlin)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.zip4j)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
