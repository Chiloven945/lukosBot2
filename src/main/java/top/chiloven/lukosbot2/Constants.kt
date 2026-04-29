package top.chiloven.lukosbot2

import java.util.*

/**
 * Singleton object for holding application-wide constants.
 */
object Constants {

    const val VERSION = "0.3.0-SNAPSHOT"
    const val APP_NAME = "lukosBot2"
    const val UA = "$APP_NAME/$VERSION"

    val javaVersion: String = "%s (%s)".format(
        System.getProperty("java.version"),
        System.getProperty("java.vendor.version")
    )
    val kotlinVersion: String = KotlinVersion.CURRENT.toString()
    val springBootVersion: String = getImplVersion("org.springframework.boot.SpringApplication")

    val tgVersion: String = getMavenVersion("org.telegram", "telegrambots-client")
    val jdaVersion: String = getImplVersion("net.dv8tion.jda.api.JDA")
    val shiroVersion: String = getImplVersion("com.mikuac.shiro.boot.Shiro")

    private fun getImplVersion(className: String): String =
        runCatching { Class.forName(className).`package`?.implementationVersion }
            .getOrNull() ?: "unknown"

    @Suppress("SameParameterValue")
    private fun getMavenVersion(groupId: String, artifactId: String): String {
        val path = "META-INF/maven/$groupId/$artifactId/pom.properties"

        return runCatching {
            Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.use { input ->
                Properties().apply { load(input) }.getProperty("version")
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    }

}
