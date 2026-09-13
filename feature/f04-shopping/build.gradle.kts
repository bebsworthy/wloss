plugins {
    id("wlo.android.library.compose")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    // MVI-lite state holder + ViewModel DSL (koin-android carries the
    // lifecycle-viewmodel types the state holder extends).
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // D6: numbers render only via designsystem provenance components.
    implementation(project(":core:designsystem"))

    // Feature spine doors (ARCHITECTURE §2.2): model/engines/data/ports only.
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:engines"))
    implementation(project(":core:data"))
    // The pantry check-in reuses the M4 capture stack's doors (BarcodeScanner,
    // OffRepository) — implementations stay invisible (D1); :app binds them.
    implementation(project(":core:ports"))
    // The R-S5 deduction toggle + one-time prompt live in settings.
    implementation(project(":core:datastore"))

    testImplementation(libs.kotlin.test.junit)
}
