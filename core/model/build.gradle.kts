plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    api(libs.jspecify)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
