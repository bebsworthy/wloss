/**
 * `wlo.android.library.compose` — the Compose flavor of `wlo.android.library`:
 * adds the Kotlin Compose compiler plugin and the `compose` build feature on
 * top of the base Android-library convention. Kept as a sibling convention
 * (not a per-module plugin line) so the base convention stays Compose-free for
 * restricted impls like `:core:network`.
 */
plugins {
    id("wlo.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures {
        compose = true
    }
}
