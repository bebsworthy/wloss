/*
 * :benchmarks — the M5 performance harness (WLO-0027 PART A; ARCHITECTURE
 * §2.6 "Macrobenchmark budgets for F03 ≤ 100 ms re-deals").
 *
 * MODULE SHAPE: the macrobenchmark module shape (`com.android.test`,
 * self-instrumenting, `benchmark` buildType, targeting :app for variant
 * alignment) exactly as the brief specifies.
 *
 * WHY THE MICROBENCHMARK RULE: PlannerEngine is a D7-pure function with no
 * app-process surface — macrobenchmark metrics (StartupTimingMetric, frame
 * timing) measure a TARGET app process that the planner never renders in,
 * so a MacrobenchmarkRule run would time app startup and measure nothing of
 * the engine. `androidx.benchmark.junit4.BenchmarkRule` measures the code
 * IN THIS PROCESS with the same warmup/iteration discipline and
 * compilation-mode control, and writes the standard `androidx.benchmark`
 * logcat line + JSON output. The recorded M5 baseline: see REPORT in the
 * milestone hand-off (emulator wlo-api29).
 */
plugins {
    id("com.android.test")
    id("wlo.quality")
}

android {
    namespace = "app.wlo.benchmarks"
    compileSdk = app.wlo.buildlogic.AndroidConfig.COMPILE_SDK

    defaultConfig {
        minSdk = app.wlo.buildlogic.AndroidConfig.MIN_SDK
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        // The M5 baseline runs on the wlo-api29 emulator (per the milestone
        // brief): EMULATOR is the documented suppression for that. Emulator
        // runs cannot AOT-compile the benchmark package (NOT-AOT-COMPILED),
        // so this baseline is a JIT-warmed upper bound — the honest reading
        // of the recorded number; CI on hardware can drop the suppression.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
            "EMULATOR,NOT-AOT-COMPILED"
    }

    targetProjectPath = ":app"
    // AGP 9 note: `testBuildType` no longer exists in the DSL; the module's
    // own `benchmark` buildType (below) matches :app's by the same name via
    // matchingFallbacks, and the connected-check tasks pick the variant.

    buildTypes {
        create("benchmark") {
            // The microbenchmark rule measures THIS process: debuggable would
            // skew every number (and the library hard-errors on it).
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.junit4)
    implementation(libs.androidx.test.ext.junit)
    // The code under measurement (engines pull :core:model transitively; both
    // are non-restricted modules — D1 untouched).
    implementation(project(":core:engines"))
}
