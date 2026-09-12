import org.gradle.api.JavaVersion

/**
 * `wlo.application` — the `:app` composition root (nav graph, Koin wiring,
 * merged manifest). The ONLY module allowed to declare INTERNET (D4) and the
 * only one that sees the restricted impls (D1). ADR-005 §3: application id
 * `app.wlo`. AGP 9 built-in Kotlin support (no org.jetbrains.kotlin.android).
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("wlo.quality")
}

android {
    namespace = app.wlo.buildlogic.AndroidConfig.namespaceFor(project.path)
    compileSdk = app.wlo.buildlogic.AndroidConfig.COMPILE_SDK

    defaultConfig {
        applicationId = app.wlo.buildlogic.AndroidConfig.APPLICATION_ID
        minSdk = app.wlo.buildlogic.AndroidConfig.MIN_SDK
        targetSdk = app.wlo.buildlogic.AndroidConfig.TARGET_SDK
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
    }
}

// D4 backstop: the merged debug manifest is inspected as part of
// `checkArchitecture` (root wires the dependency; AGP produces the output).
tasks.register("checkMergedManifest", app.wlo.buildlogic.arch.CheckMergedManifestTask::class) {
    val manifestTask = tasks.named("processDebugMainManifest")
    dependsOn(manifestTask)
    mergedManifests.from(manifestTask.map { it.outputs.files })
}
