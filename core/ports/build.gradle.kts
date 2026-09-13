plugins {
    id("wlo.kotlin.multiplatform.core")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            // Sink/paths in the EgressPort signatures (dispatcher pumps bytes
            // into caller-provided sinks; zoo manager owns storage).
            api(libs.okio)
        }
    }
}
