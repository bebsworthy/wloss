plugins {
    id("wlo.android.library.compose")
    // The R-S4 remembered CSV mapping rides a @Serializable value (JSON in
    // the settings store).
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    // The SAF pickers (ACTION_OPEN_DOCUMENT_TREE / OPEN_DOCUMENT / CREATE).
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    // MVI-lite state holder + ViewModel DSL.
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // D6: numbers render only via designsystem components.
    implementation(project(":core:designsystem"))

    // Shared pure formatting (byte counts across the vault surfaces).
    implementation(project(":core:common"))

    // The data-mechanics port (D1: the backup/restore pipeline is bound in
    // :app) + the typed settings store (folder uri, auto toggle, app lock).
    implementation(project(":core:ports"))
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit)
}
