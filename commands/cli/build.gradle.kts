plugins {
    id("lukos.spring-library")
}

dependencies {
    implementation(project(":command:framework"))
    implementation(project(":core:runtime"))
    implementation(project(":core:model"))

    implementation(libs.spring.boot.starter)
}
