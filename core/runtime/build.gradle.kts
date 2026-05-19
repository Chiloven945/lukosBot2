plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":shared"))
    implementation(project(":properties"))
    implementation(project(":platform:api"))
    implementation(project(":command:framework"))
    implementation(project(":infra:http"))
    implementation(libs.commons.net)
    implementation(libs.snakeyaml)
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j2.impl)
    runtimeOnly(libs.h2)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
