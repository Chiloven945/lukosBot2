plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    api(project(":core:model"))
    api(project(":platform:api"))
    api(project(":shared"))
    api(project(":properties"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
