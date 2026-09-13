plugins {
    id("wlo.android.kmp.library")
    alias(libs.plugins.kotlin.serialization) // the versioned backup document (ADR-004 rules)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Ports (BackupScheduler/BiometricGate live here), consent chain
            // verification, document house rules (ADR-004 funnel), the data
            // spine (assembler + DayProjector) and its stores.
            api(project(":core:ports"))
            implementation(project(":core:model"))
            implementation(project(":core:consent"))
            implementation(project(":core:documents"))
            implementation(project(":core:database"))
            implementation(project(":core:datastore"))
            implementation(project(":core:data"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.okio)
            // WloDatabase's supertype must be on the compile classpath (same
            // note as :core:data — :core:database keeps it internal).
            implementation(libs.androidx.room3.runtime)
            // T-F2: Argon2id for the passphrase KDF — javax.crypto has no KDF;
            // BC's LIGHTWEIGHT API (org.bouncycastle.crypto.generators.
            // Argon2BytesGenerator, verified in bcprov-jdk18on 1.86) needs no
            // provider registration. Declared ONLY here (F13 impl module).
            implementation(libs.bouncycastle.bcprov)
        }
        androidMain.dependencies {
            // BackupScheduler impl (WorkManager periodic, F13 §3 auto-backup)
            // + the failure notification's NotificationCompat.
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.core.ktx)
            // PromptBiometricGate: the PLATFORM BiometricPrompt constructor
            // takes an androidx FragmentActivity (AppLock.kt FLAG note).
            implementation(libs.androidx.fragment.ktx)
            // The worker resolves the graph through the composition root.
            implementation(libs.koin.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
