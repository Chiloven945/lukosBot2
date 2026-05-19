plugins {
    alias(libs.plugins.kotlin.jvm)
}


dependencies {
    implementation(libs.jspecify)
    implementation(libs.log4j.api)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
