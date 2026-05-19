plugins {
    id("lukos.spring-library")
}

dependencies {
    implementation(project(":command:framework"))
    implementation(project(":core:runtime"))
    implementation(project(":core:model"))
    implementation(project(":infra:web"))
    implementation(project(":infra:http"))
    implementation(project(":shared"))

    implementation(libs.jsoup)
    implementation(libs.spring.boot.starter)
}
