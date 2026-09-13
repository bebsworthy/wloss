plugins {
    id("wlo.android.library.compose")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Runtime permission launcher (activity-result rides activity-compose).
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)

    // The capture stack (ARCHITECTURE §2.5 DRY anchor: camera lives HERE,
    // analyzers in :core:ai; both behind :core:ports). CapturedFrame is the
    // shared frame currency.
    api(project(":core:ports"))

    // CameraX (verified pin 1.6.2 in the version catalog).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.kotlin.test.junit)
}
