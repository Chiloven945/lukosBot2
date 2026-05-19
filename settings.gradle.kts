pluginManagement {
    includeBuild("build-logic")

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
include(":config")

include(":core:model")
include(":core:runtime")

include(":command:framework")

include(":platform:api")
include(":platform:telegram")
include(":platform:discord")
include(":platform:onebot")

include(":infra:spring")
include(":infra:http")
include(":infra:web")

include(":commands:basic")
include(":commands:admin")
include(":commands:integrations")
include(":commands:minecraft")
include(":commands:web")
include(":commands:media")
include(":commands:translate")
include(":commands:cli")
