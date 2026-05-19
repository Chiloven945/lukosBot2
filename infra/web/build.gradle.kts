plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core:model"))
    implementation(project(":properties"))
    implementation(project(":infra:http"))
    implementation(project(":infra:spring"))
    implementation(libs.selenium.java)
    implementation(libs.jsoup)
    implementation(libs.webdrivermanager)
    implementation(libs.flexmark.html2md.converter)
    implementation(libs.spring.boot.starter)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
