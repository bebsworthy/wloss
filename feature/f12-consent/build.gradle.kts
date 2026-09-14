plugins {
    id("wlo.android.library.compose")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    // MVI-lite state holder + ViewModel DSL.
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // D6: numbers render only via designsystem components.
    implementation(project(":core:designsystem"))

    // Shared pure formatting (byte counts on receipts / consent sheets).
    implementation(project(":core:common"))

    // The frozen six-category consent taxonomy + ledger/gate doors (R-C1);
    // egress receipt AUDIT via the port (D1: the ledger impl is :app's bind).
    implementation(project(":core:consent"))
    implementation(project(":core:ports"))
    // Kill switch + diagnostics toggles (typed settings flows).
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))

    testImplementation(libs.kotlin.test.junit)
}
