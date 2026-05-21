plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.lombok)
}

dependencies {
    api(project(":infrastructure:http"))

    implementation(project(":shared"))
    implementation(project(":core:model"))
    implementation(project(":properties"))
    implementation(project(":infrastructure:spring"))

    api(libs.jsoup)
    api(libs.selenium.java)

    implementation(libs.flexmark.html2md.converter)
    implementation(libs.webdrivermanager)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
