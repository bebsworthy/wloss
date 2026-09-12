plugins {
    id("wlo.application")
}

android {
    defaultConfig {
        // AndroidX Test Orchestrator: every instrumented test starts from
        // cleared app data + a fresh app process — the onboarding acceptance
        // tests rely on that cold-install semantics.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    testOptions {
        // Route the run through AndroidTestOrchestrator (installed via
        // androidTestUtil below); the default engine ignores clearPackageData.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Composition root (D1): Koin graph + the only module that binds impls.
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.annotations)
    // The root binds the Room builder type itself (androidDatabaseBuilder).
    implementation(libs.androidx.room3.runtime)
    // ... and declares the DataStore type SettingsStore wraps.
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.datetime)
    implementation(libs.okio)

    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:engines"))
    implementation(project(":core:documents"))
    implementation(project(":feature:f01-onboarding"))
    implementation(project(":feature:f02-food"))
    implementation(project(":feature:f06-weight"))
    implementation(project(":feature:f10-daily-hub"))

    // M3 perf harness: the instrumented FoodSearchPerfTest seeds through
    // DiarySeeder (:core:testing is otherwise test-only per ARCHITECTURE §2.2).
    androidTestImplementation(project(":core:testing"))

    // UiDevice shell commands (am force-stop) in instrumented tests.
    androidTestImplementation(libs.androidx.test.uiautomator)
    // Each instrumented test starts from cleared app data + a fresh app
    // process (the orchestrator process survives; the app's does not).
    androidTestUtil(libs.androidx.test.orchestrator)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // JVM unit tests: the concrete kotlin-test binding (JUnit4-backed) — AGP's
    // built-in Kotlin has no KGP to pick the KMP variant for us.
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.koin.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
