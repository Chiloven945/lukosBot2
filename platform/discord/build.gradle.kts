plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":platform:api"))
    implementation(project(":core:model"))
    implementation(project(":core:runtime"))
    implementation(project(":command:framework"))
    implementation(project(":properties"))
    implementation(project(":infra:http"))
    implementation(project(":infra:spring"))
    implementation(project(":shared"))
    implementation(libs.jda)
    implementation(libs.spring.boot.starter)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
