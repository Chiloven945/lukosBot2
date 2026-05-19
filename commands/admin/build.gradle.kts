plugins {
    id("lukos.spring-library")
}

dependencies {
    implementation(project(":command:framework"))
    implementation(project(":core:runtime"))
    implementation(project(":core:model"))
    implementation(project(":platform:api"))
    implementation(project(":properties"))
    implementation(project(":shared"))

    implementation(libs.spring.boot.starter)
}
