/*
 * WLO — root settings. Module set is the lean M1 subset of ADR-005;
 * later milestones append :core:data, :feature:f01…f13 and the restricted
 * impls (:core:network/:core:ai/:core:media/:core:vault) without rule changes.
 */
pluginManagement {
    includeBuild("build-logic")
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
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "wlo"

include(":app")
include(":core:model")
include(":core:common")
include(":core:engines")
include(":core:ports")
include(":core:consent")
include(":core:documents")
include(":core:database")
include(":core:datastore")
include(":core:testing")
include(":core:designsystem")
