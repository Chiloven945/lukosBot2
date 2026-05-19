import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply(plugin = "java")
apply(plugin = "org.springframework.boot")
apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "org.jetbrains.kotlin.plugin.spring")
apply(plugin = "org.jetbrains.kotlin.plugin.lombok")
apply(plugin = "io.spring.dependency-management")

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val javaVersionInt = versionCatalog.findVersion("java").orElseThrow().requiredVersion.toInt()

fun VersionCatalog.library(alias: String) = findLibrary(alias).orElseThrow()

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersionInt))
    }
}

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(javaVersionInt)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersionInt)
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

configurations.configureEach {
    exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "ch.qos.logback", module = "logback-core")
}

dependencies {
    add("compileOnly", versionCatalog.library("lombok"))
    add("annotationProcessor", versionCatalog.library("lombok"))
    add("annotationProcessor", versionCatalog.library("spring-boot-configuration-processor"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
