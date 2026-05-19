plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":platform:api"))
    implementation(project(":shared"))
    implementation(project(":properties"))
    implementation(libs.spring.boot.starter)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit)
}
