import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * `wlo.kotlin.multiplatform.core` — pure KMP core module (ARCHITECTURE §2.2):
 * `commonMain` + a JVM purity target (ADR-001). Modules that need an Android
 * target use `wlo.android.kmp.library` instead. Enforces D5 (`explicitApi()`).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("wlo.quality")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    explicitApi()
}

// The commonTest* configurations appear once KGP finalizes the JVM target's
// source sets — attach the test dependency when they show up.
val kotlinTest = libs.findLibrary("kotlin-test").get()
configurations.matching { it.name == "commonTestImplementation" }.configureEach {
    dependencies.addLater(kotlinTest)
}
