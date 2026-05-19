plugins {
    id("lukos.spring-library")
}

dependencies {
    implementation(project(":platform:api"))
    implementation(project(":core:model"))
    implementation(project(":core:runtime"))
    implementation(project(":command:framework"))
    implementation(project(":config"))
    implementation(project(":infra:http"))
    implementation(project(":shared"))

    implementation(libs.jda)
    implementation(libs.spring.boot.starter)
}
