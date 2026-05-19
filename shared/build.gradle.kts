plugins {
    id("lukos.kotlin-library")
}

dependencies {
    implementation(libs.jackson.core)
    implementation(libs.jackson.core.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.zip4j)
    implementation(libs.log4j.api)
}
