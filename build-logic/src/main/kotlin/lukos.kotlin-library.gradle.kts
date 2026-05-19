import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply(plugin = "java-library")
apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "org.jetbrains.kotlin.plugin.lombok")

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val javaVersionInt = versionCatalog.findVersion("java").orElseThrow().requiredVersion.toInt()

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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
