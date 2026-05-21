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
    implementation(libs.docker.java)
    implementation(libs.docker.java.transport.httpclient5)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
