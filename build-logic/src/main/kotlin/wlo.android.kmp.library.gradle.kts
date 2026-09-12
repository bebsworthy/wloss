import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * `wlo.android.kmp.library` — KMP core module with an Android library target
 * (AGP 9 built-in Kotlin plugin `com.android.kotlin.multiplatform.library`,
 * NOT plain `com.android.library`) plus the JVM purity target (ADR-005 §2).
 * Used by `:core:database`; usable later by any core module that touches the
 * platform. Enforces D5 (`explicitApi()`).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("wlo.quality")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

kotlin {
    targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
        namespace = app.wlo.buildlogic.AndroidConfig.namespaceFor(project.path)
        compileSdk = app.wlo.buildlogic.AndroidConfig.COMPILE_SDK
        minSdk = app.wlo.buildlogic.AndroidConfig.MIN_SDK
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    explicitApi()
}

// Test configurations appear once KGP/AGP finalize the targets' source sets —
// attach the test dependency when each shows up.
val kotlinTest = libs.findLibrary("kotlin-test").get()
configurations.matching {
    it.name in setOf("commonTestImplementation", "androidTestImplementation")
}.configureEach {
    dependencies.addLater(kotlinTest)
}
