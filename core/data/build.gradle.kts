plugins {
    id("wlo.android.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:common"))
            api(project(":core:documents"))
            implementation(project(":core:database"))
            implementation(project(":core:datastore"))
            implementation(project(":core:engines"))
            // WloDatabase's supertype (RoomDatabase) must be on the compile
            // classpath — :core:database keeps it `implementation`-scoped.
            implementation(libs.androidx.room3.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.datetime)
            implementation(libs.okio)
            implementation(libs.kotest.property)
        }
    }
}
