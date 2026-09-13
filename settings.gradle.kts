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
include(":core:data")
include(":core:datastore")
include(":core:network")
include(":core:ai")
include(":core:media")
include(":core:vault")
include(":core:testing")
include(":benchmarks")
include(":core:designsystem")
include(":feature:f01-onboarding")
include(":feature:f02-food")
include(":feature:f03-planning")
include(":feature:f04-shopping")
include(":feature:f06-weight")
include(":feature:f10-daily-hub")
include(":feature:f12-consent")
include(":feature:f13-vault")
