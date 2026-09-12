package app.wlo.buildlogic.arch

import java.io.File
import java.nio.file.Path
import kotlin.text.MatchResult

/**
 * The enforced dependency rules of ARCHITECTURE.md §2.3 / ADR-005, as pure
 * functions over paths and dependency edges. The same code runs in the real
 * build (`checkArchitecture`) and in the TestKit self-tests, so the checks
 * cannot silently rot.
 *
 * D8 (no exceptions across module boundaries) is deliberately NOT machine-
 * checked in M1: it is a documented convention enforced by review + the detekt
 * config comment (see config/detekt/detekt.yml). Custom detekt rules are a
 * follow-up.
 */
public object ArchRules {

    /** D1 — restricted implementation modules: visible only to [RESTRICTED_VISIBLE_TO]. */
    public val RESTRICTED_MODULES: List<String> =
        listOf(":core:network", ":core:ai", ":core:media", ":core:vault")

    /** D1 — the allowlist (data-driven; today only the composition root). */
    public val RESTRICTED_VISIBLE_TO: Set<String> = setOf(":app")

    /** D7 — clock/time sourcing banned inside pure engines. */
    public val D7_CLOCK_USAGE: Regex =
        Regex("\\bClock\\.System\\b|\\bjava\\.time\\.Clock\\b|\\bInstant\\.now\\s*\\(|\\bkotlinx\\.datetime\\.Clock\\b")

    /**
     * D3 — commonMain must never import the Android platform or Android-only
     * androidx libraries. Genuinely KMP androidx groups are allowlisted in
     * [D3_KMP_ANDROIDX_ALLOWLIST] (ADR-003 puts Room 3, DataStore and the
     * sqlite driver into commonMain on purpose); the JVM purity target is the
     * second guard.
     */
    public val D3_ANDROID_IMPORT: Regex =
        Regex("^import\\s+(android[x]?\\.[A-Za-z_][A-Za-z0-9_.]*)", RegexOption.MULTILINE)

    /** androidx groups that are KMP artifacts and therefore legal in commonMain. */
    public val D3_KMP_ANDROIDX_ALLOWLIST: List<String> =
        listOf("androidx.room3.", "androidx.datastore.", "androidx.sqlite.")

    /**
     * D6 heuristic (documented): a `Text(` call whose argument unwraps a
     * DerivedValue-shaped receiver (camelCase identifier ending in `Value` or
     * `Dv`, or containing `Derived`) with `.value`. The strong D6 enforcement
     * is type-level in `:core:designsystem` (provenance-chip components) and
     * is PART B's job; this grep is the tripwire.
     */
    public val D6_RAW_DERIVED_TEXT: Regex =
        Regex("Text\\(\\s*[A-Za-z_][A-Za-z0-9_]*(?:(?:Value|Dv)\\b|(?:Derived)[A-Za-z0-9_]*)\\.value")

    /** D1 + D2: project-dependency edges that must not exist. */
    public fun dependencyViolations(edges: List<ProjectEdge>): List<Violation> {
        val violations = mutableListOf<Violation>()
        for (edge in edges) {
            if (edge.to in RESTRICTED_MODULES && edge.from !in RESTRICTED_VISIBLE_TO) {
                violations += Violation(
                    rule = "D1",
                    message = "$edge: restricted module '${edge.to}' is only visible to " +
                        "$RESTRICTED_VISIBLE_TO, but '${edge.from}' depends on it. " +
                        "Features own ports; :app binds implementations.",
                )
            }
            if (edge.from.startsWith(":feature:") && edge.to.startsWith(":feature:")) {
                violations += Violation(
                    rule = "D2",
                    message = "$edge: feature-to-feature dependency. Navigate via route " +
                        "contracts; shared UI belongs in :core:designsystem.",
                )
            }
        }
        return violations
    }

    /** D3: no `import android.*` and no non-KMP `import androidx.*` in commonMain. */
    public fun commonMainViolations(commonMainDirs: Map<String, List<Path>>): List<Violation> =
        commonMainDirs
            .flatMap { (projectPath, dirs) ->
                dirs.flatMap { dir ->
                    dir.sourcesMatching(D3_ANDROID_IMPORT) { match -> !isKmpAndroidxImport(match.groupValues[1]) }
                }
                    .map { hit -> projectPath to hit }
            }
            .map { (projectPath, hit) ->
                Violation(
                    "D3",
                    "$projectPath: ${hit.file}: Android import in commonMain ('${hit.text}') — " +
                        "commonMain stays platform-free (ADR-001); KMP androidx allowlist: " +
                        "${D3_KMP_ANDROIDX_ALLOWLIST.joinToString()}.",
                )
            }

    private fun isKmpAndroidxImport(importFqn: String): Boolean =
        D3_KMP_ANDROIDX_ALLOWLIST.any { importFqn.startsWith(it) }

    /** D7: engines are instant-in/value-out — no Clock.now(), no global time. */
    public fun enginePurityViolations(engineSourceDirs: Map<String, List<Path>>): List<Violation> =
        engineSourceDirs
            .flatMap { (projectPath, dirs) ->
                dirs.flatMap { dir -> dir.sourcesMatching(D7_CLOCK_USAGE) }.map { hit -> projectPath to hit }
            }
            .map { (projectPath, hit) ->
                Violation(
                    "D7",
                    "$projectPath: ${hit.file}: engine sources must not read the clock " +
                        "('${hit.text}'). Engines take Instant/values as parameters.",
                )
            }

    /**
     * D4 (module-manifest layer): INTERNET may only appear in :app. The merged
     * manifest is checked separately by `checkMergedManifest` on :app.
     */
    public fun manifestViolations(manifests: Map<String, List<Path>>): List<Violation> =
        manifests.flatMap { (projectPath, files) ->
            if (projectPath == ":app") {
                emptyList()
            } else {
                files.filter { file -> file.toFile().readText().contains("android.permission.INTERNET") }
                    .map {
                        Violation(
                            "D4",
                            "$projectPath: ${it.toAbsolutePath()} declares " +
                                "android.permission.INTERNET — only :app may declare it.",
                        )
                    }
            }
        }

    /** D6 heuristic: raw DerivedValue `.value` renders inside `Text(...)` in UI modules. */
    public fun derivedRenderingViolations(uiSourceFiles: Map<String, List<Path>>): List<Violation> =
        uiSourceFiles
            .flatMap { (projectPath, files) ->
                files.flatMap { file -> file.sourcesMatching(D6_RAW_DERIVED_TEXT) }
                    .map { hit -> projectPath to hit }
            }
            .map { (projectPath, hit) ->
                Violation(
                    "D6",
                    "$projectPath: ${hit.file}: raw derived number rendered via " +
                        "Text(${hit.text}). Use provenance-chip components from :core:designsystem.",
                )
            }

    /**
     * All Kotlin sources under [dir] whose text matches [regex]; [keep] filters
     * matched lines (e.g. allowlisted imports) before reporting.
     */
    private fun Path.sourcesMatching(
        regex: Regex,
        keep: (MatchResult) -> Boolean = { true },
    ): List<SourceHit> {
        val root = toFile()
        if (!root.exists()) return emptyList()
        return root
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readText().let { text ->
                    regex.findAll(text)
                        .filter { keep(it) }
                        .map { SourceHit(file.path, it.value) }
                }
            }
            .toList()
    }
}

/** One module→module classpath edge, owner first. */
public data class ProjectEdge(public val from: String, public val to: String) {
    override fun toString(): String = "$from -> $to"
}

/** A failed rule: machine-readable id + human message naming the rule. */
public data class Violation(public val rule: String, public val message: String)

/** (absolute file path, matched text) for a source-scan violation. */
public data class SourceHit(public val file: String, public val text: String)
