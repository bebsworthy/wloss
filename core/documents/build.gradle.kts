plugins {
    id("wlo.kotlin.multiplatform.core")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}
