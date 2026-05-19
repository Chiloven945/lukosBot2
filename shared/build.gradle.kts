plugins {
    id("lukos.kotlin-library")
}

dependencies {
    api(libs.jackson.core)
    api(libs.jackson.core.databind)
    api(libs.jackson.module.kotlin)
    api(libs.log4j.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.zip4j)
}
