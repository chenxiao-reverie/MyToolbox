pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyToolbox"

include(":app")
include(":core:model")
include(":core:ui")
include(":core:task")
include(":core:media")
include(":core:storage")
include(":engine:api")
include(":engine:platform")
include(":feature:home")
include(":feature:tool")
include(":feature:queue")
include(":feature:diary")