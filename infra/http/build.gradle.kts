plugins {
    id("lukos.spring-library")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":infra:spring"))

    api(libs.okhttp)
    implementation(libs.spring.boot.starter)
}
