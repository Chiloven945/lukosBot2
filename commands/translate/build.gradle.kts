plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":command:framework"))
    implementation(project(":core:runtime"))
    implementation(project(":core:model"))
    implementation(project(":properties"))
    implementation(project(":shared"))
    implementation(libs.docker.java)
    implementation(libs.docker.java.transport.httpclient5)
    implementation(libs.spring.boot.starter)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
