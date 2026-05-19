plugins {
    id("lukos.spring-library")
}

dependencies {
    api(project(":core:model"))
    api(project(":platform:api"))
    api(project(":shared"))

    implementation(libs.spring.boot.starter)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit)
}
