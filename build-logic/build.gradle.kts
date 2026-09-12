plugins {
    `kotlin-dsl`
}

dependencies {
    // Plugin artifacts applied/configured by the convention plugins. `implementation`
    // so they are on the consumer classpath when the conventions `plugins { id(...) }`.
    implementation(libs.agp)
    implementation(libs.kgp)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.room3.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    // TestKit fixtures need a stable working dir (build-logic) for includeBuild resolution
    systemProperty("wlo.buildlogic.dir", projectDir.absolutePath)
}
