plugins {
    id("lukos.spring-library")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core:model"))
    implementation(project(":infra:http"))
    implementation(project(":infra:spring"))

    implementation(libs.selenium.java)
    implementation(libs.jsoup)
    implementation(libs.webdrivermanager)
    implementation(libs.flexmark.html2md.converter)
    implementation(libs.spring.boot.starter)
}
