plugins {
    id("lukos.spring-library")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.okhttp)
    implementation(libs.spring.boot.starter)
}
