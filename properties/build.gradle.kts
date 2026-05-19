plugins {
    id("lukos.spring-library")
}

dependencies {
    implementation(project(":infra:spring"))
    implementation(libs.spring.boot.starter)
}
