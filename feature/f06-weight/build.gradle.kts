plugins {
    id("wlo.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // MVI-lite state holder + ViewModel DSL (koin-android carries the
    // lifecycle-viewmodel types the state holder extends).
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.annotations)

    // D6: numbers render only via designsystem provenance components.
    implementation(project(":core:designsystem"))

    // Feature spine doors (ARCHITECTURE §2.2): model/engines/data only.
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:engines"))
    implementation(project(":core:documents"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okio)
}
