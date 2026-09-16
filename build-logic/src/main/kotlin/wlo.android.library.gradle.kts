import org.gradle.api.JavaVersion

/**
 * `wlo.android.library` — Android-only library module base (AGP 9 built-in
 * Kotlin support; org.jetbrains.kotlin.android is obsolete). NO Compose:
 * compose-compiling modules apply `wlo.android.library.compose` instead, so
 * non-UI restricted impls (`:core:network`) never drag the Compose compiler +
 * runtime requirement into their compilations. Enforces D5 (`explicitApi()`).
 */
plugins {
    id("com.android.library")
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

    lint {
        abortOnError = true
    }
}

kotlin {
    explicitApi()
}
