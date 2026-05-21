plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    implementation(project(":core:command"))
    implementation(project(":core:model"))
    implementation(project(":properties"))
    implementation(project(":shared"))
    implementation(project(":core:runtime"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
