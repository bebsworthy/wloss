plugins {
    id("wlo.android.kmp.library")
    alias(libs.plugins.kotlin.serialization) // zoo manifest is a versioned document
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ModelManager + EgressPort are this module's public contract.
            api(project(":core:ports"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // Zoo storage: app-private files, HashingSink sha256 in the same
            // pass as the download, atomicMove for install (all pure okio).
            implementation(libs.okio)
            // Instant in the selector's provenance stamp (injected — no clock).
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            // ADR-006 pin: ONNX Runtime Mobile executes the zoo models fully
            // on-device (audit card: docs/tech/audit/onnxruntime.md).
            implementation(libs.onnxruntime.android)
            // ML Kit bundled barcode + latin text recognition — R-S13
            // audit-carded (docs/tech/audit/mlkit-*.md), R-S14 tracked
            // distribution exception for their built-in models.
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.mlkit.text.recognition)
            // Apache-2.0 ZXing fallback behind the same BarcodeScanner port.
            implementation(libs.zxing.android.embedded)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // Localhost-only wire for the state-machine tests (D9 name-exempt:
            // a mock server binds a socket, it never opens one).
            implementation(libs.okhttp.mockwebserver3)
        }
    }
}
