plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    api(project(":core:command"))
    api(project(":properties"))
    api(project(":core:model"))
    api(project(":infrastructure:http"))
    api(project(":platform:api"))
    api(project(":shared"))

    implementation(libs.slf4j.api)
    implementation(libs.spring.boot.starter.jdbc)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
