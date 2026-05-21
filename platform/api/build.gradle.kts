plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    api(project(":core:model"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
