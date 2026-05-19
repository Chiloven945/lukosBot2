plugins {
    id("lukos.spring-library")
}

dependencies {
    implementation(project(":command:framework"))
    implementation(project(":core:runtime"))
    implementation(project(":core:model"))
    implementation(project(":properties"))
    implementation(project(":shared"))

    implementation(libs.commons.net)
    implementation(libs.spring.boot.starter)
}
