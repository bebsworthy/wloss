plugins {
    id("wlo.application")
}

android {
    buildTypes {
        // Macrobenchmark-module shape (:benchmarks targets this app): a
        // release-like build signed with the debug key for local/CI baseline
        // runs (M5, WLO-0027 PART A).
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

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
    // Restricted impls (D1: composition-root-only) — bound in di/WloModule.
    implementation(project(":core:network"))
    implementation(project(":core:ai"))
    implementation(project(":core:media"))
    implementation(project(":core:ports"))
    implementation(project(":core:consent"))
    implementation(project(":feature:f01-onboarding"))
    implementation(project(":feature:f02-food"))
    implementation(project(":feature:f03-planning"))
    implementation(project(":feature:f04-shopping"))
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
    testImplementation(libs.kotlinx.coroutines.test)
    // Localhost wire for the dispatcher+zoo integration test (D9 name-exempt:
    // mock servers bind, they never egress).
    testImplementation(libs.okhttp.mockwebserver3)

    // M4 E2E: zxing's EAN-13 ENCODER renders test barcodes (decode via the
    // port; encode is a test-side capability — no shipping dependency). The
    // version follows zxing-android-embedded's strict core pin (3.4.1).
    androidTestImplementation("com.google.zxing:core:3.4.1")
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    // kotlin.test assertions for the M4 instrumented E2E.
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
