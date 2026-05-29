/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
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
