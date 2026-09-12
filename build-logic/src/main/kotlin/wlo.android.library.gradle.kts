import org.gradle.api.JavaVersion

/**
 * `wlo.android.library` — Android-only library module with Compose
 * (`:core:designsystem` today, `:feature:*` and restricted impls later).
 * AGP 9 provides built-in Kotlin support (org.jetbrains.kotlin.android is
 * obsolete); the compose-compiler Gradle plugin is applied here so every
 * Compose-compiling module gets it automatically.
 */
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("wlo.quality")
}

android {
    namespace = app.wlo.buildlogic.AndroidConfig.namespaceFor(project.path)
    compileSdk = app.wlo.buildlogic.AndroidConfig.COMPILE_SDK

    defaultConfig {
        minSdk = app.wlo.buildlogic.AndroidConfig.MIN_SDK
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Lint stays informational for M1; architecture rules live in checkArchitecture.
        abortOnError = false
    }
}

kotlin {
    explicitApi()
}
