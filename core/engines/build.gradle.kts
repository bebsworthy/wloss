plugins {
    id("wlo.kotlin.multiplatform.core")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api: engine outputs are @Serializable public API (D5 hygiene).
            api(project(":core:model"))
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.kotlinx.serialization.json)
            // Property tests (T-J): kotest-property as a library inside kotlin-test.
            implementation(libs.kotest.property)
        }
    }
}
