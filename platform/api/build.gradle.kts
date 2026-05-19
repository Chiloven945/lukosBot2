plugins {
    alias(libs.plugins.kotlin.jvm)
}


dependencies {
    implementation(project(":core:model"))
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
