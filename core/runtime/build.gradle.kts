plugins {
    id("lukos.spring-library")
}

dependencies {
    api(project(":core:model"))
    api(project(":shared"))
    implementation(project(":properties"))

    implementation(libs.commons.net)
    implementation(libs.snakeyaml)
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.jdbc)
    runtimeOnly(libs.h2)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j2.impl)
}
