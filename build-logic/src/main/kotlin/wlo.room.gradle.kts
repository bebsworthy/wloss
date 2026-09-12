import androidx.room3.gradle.RoomExtension

/**
 * `wlo.room` — Room 3 (`androidx.room3`) persistence convention (ADR-003).
 * Room 3 is Kotlin-first, KSP2-only, suspend-only; the Gradle plugin exports
 * schemas into `<module>/schemas` (checked into the repo — they feed the
 * migration tests). Module adds `kspJvm`/`kspAndroid` compiler deps itself so
 * the configurations exist after its KMP targets are declared.
 */
plugins {
    id("com.google.devtools.ksp")
    id("androidx.room3")
}

extensions.configure(RoomExtension::class.java) {
    schemaDirectory(layout.projectDirectory.dir("schemas"))
}

// v14 ktlint-plugin quirk: the per-source-set `ktlint*Format` REPORT aliases do
// not honor the ktlint filter excludes and trip on KSP-generated sources.
// Formatting itself runs through `runKtlintFormatOver*` tasks (which do honor
// the excludes); check tasks are unaffected.
tasks.matching {
    it.name.startsWith("ktlint") && it.name.endsWith("Format") && it.name.contains("SourceSet")
}.configureEach {
    enabled = false
}
