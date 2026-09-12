plugins {
    id("wlo.kotlin.multiplatform.core")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
        }
    }
}
