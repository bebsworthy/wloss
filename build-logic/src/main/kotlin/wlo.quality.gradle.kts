/**
 * `wlo.quality` — detekt + ktlint on every Kotlin module. Both hook into
 * `check` so `./gradlew build` runs them. House detekt ruleset lives at
 * `config/detekt/detekt.yml` (D6/D7 custom rules are a documented follow-up;
 * D7 is enforced structurally by `checkArchitecture` for M1 — see
 * ARCHITECTURE.md §2.3 and ADR-005).
 */
plugins {
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files(rootProject.file("config/detekt/detekt.yml")))
}

ktlint {
    // Room/KSP and Compose-generated sources are not hand-written code.
    // Both the glob and predicate forms are needed: the check tasks honor the
    // predicate, the format tasks the globs.
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        exclude { element -> element.file.toString().contains("/generated/") }
        exclude { element -> element.file.toString().contains("/build/") }
    }
}
