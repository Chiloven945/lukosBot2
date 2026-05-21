plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    implementation(project(":platform:api"))
    implementation(project(":core:model"))
    implementation(project(":core:runtime"))
    implementation(project(":core:command"))
    implementation(project(":properties"))
    implementation(project(":infrastructure:http"))
    implementation(project(":infrastructure:spring"))
    implementation(project(":shared"))

    implementation(libs.shiro)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
