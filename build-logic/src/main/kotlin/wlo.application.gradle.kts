import org.gradle.api.JavaVersion
import java.util.Properties

/**
 * `wlo.application` — the `:app` composition root (nav graph, Koin wiring,
 * merged manifest). The ONLY module allowed to declare INTERNET (D4) and the
 * only one that sees the restricted impls (D1). ADR-005 §3: application id
 * `app.wlo`. AGP 9 built-in Kotlin support (no org.jetbrains.kotlin.android).
 *
 * Versioning (WLO-0029): `versionCode` is monotonic along the repo history
 * (`git rev-list --count HEAD`) so channel builds always install over older
 * ones and never over newer ones; CI or a local `-Pwlo.versionCode=` /
 * `WLO_VERSION_CODE=` override wins (CI injects the same count — identical
 * result). `versionName` follows the channel: `v*` tag → `x.y.z`, main tip →
 * `0.1.0-alpha.N+<sha>` via `-Pwlo.versionName=` / `WLO_VERSION_NAME=`.
 *
 * Release signing (WLO-0029): one dedicated keystore for BOTH channels, never
 * committed — loaded from `keystore.properties` (git-ignored; see
 * keystore.properties.example) or env (`WLO_SIGNING_STORE_FILE`,
 * `WLO_SIGNING_STORE_PASSWORD`, `WLO_SIGNING_KEY_ALIAS`,
 * `WLO_SIGNING_KEY_PASSWORD`). Without a config the release APK is unsigned;
 * CI refuses to publish unless every signing secret is present. A debug key is
 * never allowed onto either dogfood channel (signature change ⇒ forced
 * uninstall ⇒ data loss).
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("wlo.quality")
}

/**
 * Commit count of HEAD — monotonic by construction on main; falls back to 1
 * outside a git repo (never the case for real builds).
 */
fun commitCount(): Int =
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir = rootDir
    }.standardOutput.asText.get().trim().toIntOrNull() ?: 1

fun versionOverride(name: String): String? =
    providers.gradleProperty("wlo.$name").orNull
        ?: providers.environmentVariable("WLO_${name.uppercase()}").orNull

android {
    namespace = app.wlo.buildlogic.AndroidConfig.namespaceFor(project.path)
    compileSdk = app.wlo.buildlogic.AndroidConfig.COMPILE_SDK

    defaultConfig {
        applicationId = app.wlo.buildlogic.AndroidConfig.APPLICATION_ID
        minSdk = app.wlo.buildlogic.AndroidConfig.MIN_SDK
        targetSdk = app.wlo.buildlogic.AndroidConfig.TARGET_SDK
        val requestedVersionCode = versionOverride("versionCode")
        versionCode =
            requestedVersionCode?.let {
                requireNotNull(it.toIntOrNull()?.takeIf { code -> code > 0 }) {
                    "wlo.versionCode/WLO_VERSION_CODE must be a positive integer, got '$it'"
                }
            } ?: commitCount()
        versionName = versionOverride("versionName") ?: "0.1.0"
    }

    signingConfigs {
        val props = Properties()
        val propsFile = rootProject.file("keystore.properties")
        val environmentKeys =
            mapOf(
                "storeFile" to "WLO_SIGNING_STORE_FILE",
                "storePassword" to "WLO_SIGNING_STORE_PASSWORD",
                "keyAlias" to "WLO_SIGNING_KEY_ALIAS",
                "keyPassword" to "WLO_SIGNING_KEY_PASSWORD",
            )
        fun prop(key: String): String? =
            props.getProperty(key)
                ?: environmentKeys[key]?.let { providers.environmentVariable(it).orNull }
        if (propsFile.exists()) {
            propsFile.inputStream().use(props::load)
        }
        val storeFile = prop("storeFile")?.let { rootProject.file(it) }
        if (storeFile != null && storeFile.exists()) {
            create("channel") {
                this.storeFile = storeFile
                storePassword = prop("storePassword")
                keyAlias = prop("keyAlias")
                keyPassword = prop("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // R8 decision deferred (WLO-0029); dogfood builds unshrunk
            signingConfig = signingConfigs.findByName("channel")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Reviewed legacy warnings may be baselined, but correctness/security
        // errors must stop shipping builds (WLO-0065).
        abortOnError = true
    }
}

// D4/D10 backstop: both shipping manifest shapes are inspected as part of
// `checkArchitecture` (root wires the dependency; AGP produces the outputs).
// The rule XML is an input too, so weakening an exclusion fails the same gate.
tasks.register("checkMergedManifest", app.wlo.buildlogic.arch.CheckMergedManifestTask::class) {
    val debugManifestTask = tasks.named("processDebugManifest")
    val releaseManifestTask = tasks.named("processReleaseManifest")
    dependsOn(debugManifestTask, releaseManifestTask)
    debugMergedManifests.from(debugManifestTask.map { it.outputs.files })
    releaseMergedManifests.from(releaseManifestTask.map { it.outputs.files })
    backupRuleFiles.from(
        layout.projectDirectory.file("src/main/res/xml/backup_rules.xml"),
        layout.projectDirectory.file("src/main/res/xml/data_extraction_rules.xml"),
    )
}
