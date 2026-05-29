plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.lombok)
    alias(libs.plugins.spring.boot)
}

springBoot {
    mainClass.set("top.chiloven.lukosbot2.Main")
}

@Suppress("UNCHECKED_CAST")
val lukosDisplayVersionProvider =
    rootProject.extra["lukosDisplayVersionProvider"] as Provider<String>

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("lukosBot2")
    archiveFileName.set("${archiveBaseName.get()}-${lukosDisplayVersionProvider.get()}.jar")
}

tasks.register("releaseBootJar") {
    group = "build"
    description = "Builds release JAR (no commit hash)"
    dependsOn(tasks.named("bootJar"))
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":properties"))
    implementation(project(":core:model"))
    implementation(project(":core:runtime"))
    implementation(project(":core:command"))
    implementation(project(":platform:api"))
    implementation(project(":infrastructure:http"))
    implementation(project(":infrastructure:spring"))

    implementation(project(":platform:telegram"))
    implementation(project(":platform:discord"))
    implementation(project(":commands:basic"))
    implementation(project(":commands:admin"))
    implementation(project(":commands:integrations"))
    implementation(project(":commands:minecraft"))
    implementation(project(":commands:web"))
    implementation(project(":commands:media"))
    implementation(project(":commands:translate"))
    implementation(project(":commands:cli"))

    implementation(libs.kotlin.reflect)
    implementation(libs.snakeyaml)
    implementation(libs.spring.boot.starter.log4j2)

    runtimeOnly(libs.h2)

    compileOnly(libs.lombok)
    annotationProcessor(platform(libs.spring.boot.dependencies.bom))
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
