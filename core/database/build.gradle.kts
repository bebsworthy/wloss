plugins {
    id("wlo.android.kmp.library")
    id("wlo.room")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(libs.androidx.room3.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.room3.testing)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    // Room 3 on KMP: KSP2 processes commonMain symbols per target compilation.
    add("kspJvm", libs.androidx.room3.compiler)
    add("kspAndroid", libs.androidx.room3.compiler)
}
