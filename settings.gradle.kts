pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://libraries.minecraft.net")
    }
}

rootProject.name = "lukosBot2"

include(":app")
include(":shared")
include(":properties")

include(":core:model")
include(":core:runtime")
include(":core:command")

include(":platform:api")
include(":platform:telegram")
include(":platform:discord")

include(":infrastructure:spring")
include(":infrastructure:http")
include(":infrastructure:web-infra")

include(":commands:basic")
include(":commands:admin")
include(":commands:integrations")
include(":commands:minecraft")
include(":commands:web")
include(":commands:media")
include(":commands:translate")
include(":commands:cli")
