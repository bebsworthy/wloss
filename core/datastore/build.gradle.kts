plugins {
    id("wlo.kotlin.multiplatform.core")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:common"))
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.okio)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio)
        }
    }
}
