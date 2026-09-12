plugins {
    id("wlo.kotlin.multiplatform.core")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:common"))
            // DiarySeeder computes deterministic kcal/macros via FoodMath.
            api(project(":core:engines"))
            api(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
    }
}
