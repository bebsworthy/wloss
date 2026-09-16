package app.wlo.buildlogic.arch

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Paths

/**
 * `checkArchitecture` — fails the build on any D1–D7 violation
 * (ARCHITECTURE.md §2.3 / ADR-005). Registered on the root project by
 * `wlo.architecture-check`; inputs are captured after all projects evaluate as
 * plain strings, so the action never touches a live Project (config-cache safe).
 *
 * uiAtoms (WLO-0031, corrected by WLO-0063) runs in the same action with a two-step severity
 * model: `-Pwlo.uiAtoms=enforce` (default since the P3 waves landed) fails
 * the build, making conformance mandatory; `-Pwlo.uiAtoms=warn` prints
 * counts + files per module and NEVER fails — the diagnostics baseline.
 */
public open class CheckArchitectureTask : DefaultTask() {

    /** Severity for the uiAtoms rule: "enforce" (default) or "warn". */
    @get:Input
    public var uiAtomsMode: String = MODE_ENFORCE

    public companion object {
        /** `-Pwlo.uiAtoms=warn`: print, never fail (diagnostics baseline). */
        public const val MODE_WARN: String = "warn"

        /** `-Pwlo.uiAtoms=enforce` (default): fail the build on any uiAtoms hit. */
        public const val MODE_ENFORCE: String = "enforce"
    }

    /** Project-dependency edges, lines "from|to". */
    @get:Input
    public val edgeLines: MutableCollection<String> = mutableListOf()

    /** commonMain dirs, lines "project|dir". */
    @get:Input
    public val commonMainDirLines: MutableCollection<String> = mutableListOf()

    /** Manifest files, lines "project|file". */
    @get:Input
    public val manifestLines: MutableCollection<String> = mutableListOf()

    /** UI-scoped projects' Kotlin sources, lines "project|file". */
    @get:Input
    public val uiSourceLines: MutableCollection<String> = mutableListOf()

    /** Engines source dirs, lines "project|dir". */
    @get:Input
    public val engineDirLines: MutableCollection<String> = mutableListOf()

    /** Declared external dependencies, lines "project|group|name". */
    @get:Input
    public val externalDepLines: MutableCollection<String> = mutableListOf()

    @get:Input
    public var projectPathList: List<String> = emptyList()

    @TaskAction
    public fun check() {
        val edges = edgeLines.map { line ->
            val parts = line.split('|')
            ProjectEdge(parts[0], parts[1])
        }
        val externalDeps =
            externalDepLines.map { line ->
                val parts = line.split('|')
                app.wlo.buildlogic.arch.ExternalDependency(parts[0], parts[1], parts[2])
            }
        val commonMainDirs = groupByProject(commonMainDirLines).mapValues { (_, v) -> v.map { Paths.get(it) } }
        val manifests = groupByProject(manifestLines).mapValues { (_, v) -> v.map { Paths.get(it) } }
        val uiFiles = groupByProject(uiSourceLines).mapValues { (_, v) -> v.map { Paths.get(it) } }
        val engineDirs = groupByProject(engineDirLines).mapValues { (_, v) -> v.map { Paths.get(it) } }

        val violations = buildList {
            addAll(ArchRules.dependencyViolations(edges))
            addAll(ArchRules.bannedArtifactViolations(externalDeps))
            addAll(ArchRules.commonMainViolations(commonMainDirs))
            addAll(ArchRules.manifestViolations(manifests))
            addAll(ArchRules.derivedRenderingViolations(uiFiles))
            addAll(ArchRules.enginePurityViolations(engineDirs))
        }

        val uiAtomHits = ArchRules.uiAtomViolations(uiFiles)

        if (violations.isNotEmpty()) {
            val report = violations.groupBy { it.rule }
                .toSortedMap()
                .entries.joinToString(separator = "\n") { (rule, list) ->
                    "[$rule] ${list.joinToString(separator = "\n      ") { it.message }}"
                }
            throw GradleException(
                "checkArchitecture FAILED — ${violations.size} rule violation(s):\n  $report\n" +
                    "Rules are normative: ARCHITECTURE.md §2.3 / ADR-005. Amend the master doc first.",
            )
        }

        // uiAtoms — same task path as the D-rules (`check`), own severity:
        // warn NEVER fails (migration baseline, WLO-0031 P3); enforce does.
        val moduleCount = uiAtomHits.map { it.project }.distinct().size
        when (uiAtomsMode) {
            MODE_ENFORCE ->
                if (uiAtomHits.isNotEmpty()) {
                    throw GradleException(
                        "checkArchitecture FAILED — ${uiAtomHits.size} uiAtoms violation(s) " +
                            "(mode=enforce):\n${uiAtomsReport(uiAtomHits)}\n" +
                            "Use standard Material 3 components instead of lookalikes; " +
                            "keep WLO tokens and justified custom visuals in :core:designsystem (WLO-0063). " +
                            "The lookalike rule list lives in ArchRules.UI_ATOMS_*.",
                    )
                }

            MODE_WARN ->
                if (uiAtomHits.isNotEmpty()) {
                    logger.warn(
                        "uiAtoms: WARN mode — ${uiAtomHits.size} violation(s) across $moduleCount " +
                            "module(s); WLO-0063 lookalike gate (re-run with " +
                            "-Pwlo.uiAtoms=enforce to fail the build)\n${uiAtomsReport(uiAtomHits)}",
                    )
                }

            else ->
                logger.warn(
                    "uiAtoms: unknown -Pwlo.uiAtoms value '$uiAtomsMode' (expected " +
                        "warn|enforce) — treating as warn.",
                )
        }

        logger.lifecycle(
            "checkArchitecture: OK — ${projectPathList.size} projects, D1–D7 + D9 clean " +
                "(${edges.size} dependency edges, ${externalDeps.size} external artifacts scanned). " +
                "uiAtoms: mode=$uiAtomsMode, ${uiAtomHits.size} violation(s) across $moduleCount " +
                "module(s) (-Pwlo.uiAtoms=warn|enforce; WLO-0063).",
        )
    }

    /** Per-module counts + file list, one `uiAtoms [tag]: file:line` per hit. */
    private fun uiAtomsReport(hits: List<app.wlo.buildlogic.arch.UiAtomViolation>): String =
        hits.groupBy { it.project }
            .toSortedMap()
            .entries
            .joinToString(separator = "\n") { (project, list) ->
                val files = list.map { it.file }.distinct()
                "  $project: ${list.size} violation(s) in ${files.size} file(s)\n" +
                    list.joinToString(separator = "\n") { hit ->
                        "    uiAtoms [${hit.tag}]: ${hit.file}:${hit.line}"
                    }
            }

    private fun groupByProject(lines: Collection<String>): Map<String, List<String>> =
        lines.groupBy(
            keySelector = { it.substringBefore('|') },
            valueTransform = { it.substringAfter('|') },
        )
}

/** D9 resolved-graph check, registered on each classpath-owning project. */
public open class CheckResolvedEgressDependenciesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public val productionClasspaths: ConfigurableFileCollection = project.objects.fileCollection()

    @TaskAction
    public fun check() {
        val violations = ArchRules.resolvedBannedArtifactViolations(productionClasspaths.files.map { it.toPath() })
        if (violations.isNotEmpty()) {
            throw GradleException(
                "[D9] Resolved egress dependency check failed for ${project.path}:\n  " +
                    violations.joinToString("\n  ") { it.message },
            )
        }
    }
}

/**
 * D4 — the INTERNET gate, both directions. The module-manifest scan
 * ([ArchRules.manifestViolations]) bans INTERNET everywhere except :app; THIS
 * task is the positive assertion: since M4 the dispatcher ships, :app's merged
 * manifest MUST declare INTERNET (R-S13 dropped the no-INTERNET "WLO Pure"
 * flavor — the architecture copies openScale's structure, not the letter, per
 * F12 §3.8). Fails when the merge produced no manifest or when INTERNET is
 * missing — a silent regression to a socket-less app build must not pass.
 */
public open class CheckMergedManifestTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public val debugMergedManifests: ConfigurableFileCollection = project.objects.fileCollection()

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public val releaseMergedManifests: ConfigurableFileCollection = project.objects.fileCollection()

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public val backupRuleFiles: ConfigurableFileCollection = project.objects.fileCollection()

    @TaskAction
    public fun check() {
        val violations = buildList {
            addAll(validateVariant("debug", debugMergedManifests))
            addAll(validateVariant("release", releaseMergedManifests))

            val rules = backupRuleFiles.files.filter { it.isFile }.associateBy { it.name }
            val legacy = rules["backup_rules.xml"]
            val extraction = rules["data_extraction_rules.xml"]
            if (legacy == null) {
                add("D10: missing app/src/main/res/xml/backup_rules.xml")
            } else {
                addAll(BackupPolicyValidator.legacyRulesViolations(legacy.readText()).map { "D10: backup_rules.xml $it" })
            }
            if (extraction == null) {
                add("D10: missing app/src/main/res/xml/data_extraction_rules.xml")
            } else {
                addAll(
                    BackupPolicyValidator.extractionRulesViolations(extraction.readText())
                        .map { "D10: data_extraction_rules.xml $it" },
                )
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Merged-manifest/privacy policy check failed:\n  ${violations.joinToString("\n  ")}",
            )
        }
        logger.lifecycle(
            "checkMergedManifest: debug + release manifests enforce D4 INTERNET and D10 no implicit backup/transfer; " +
                "legacy and Android 12+ extraction rules exclude all ${BackupPolicyValidator.STORAGE_DOMAINS.size} domains.",
        )
    }

    private fun validateVariant(
        variant: String,
        manifests: ConfigurableFileCollection,
    ): List<String> {
        val scanned = manifests.files.flatMap { input ->
            when {
                input.isDirectory -> input.walkTopDown().filter { it.isFile && it.name == "AndroidManifest.xml" }.toList()
                input.isFile && input.name == "AndroidManifest.xml" -> listOf(input)
                else -> emptyList()
            }
        }.distinctBy { it.absolutePath }
        if (scanned.isEmpty()) {
            return listOf("D4/D10: no $variant merged manifest found among ${manifests.files.size} output(s)")
        }
        return scanned.flatMap { manifest ->
            BackupPolicyValidator.mergedManifestViolations(manifest.readText())
                .map { violation -> "D4/D10: $variant ${manifest.absolutePath}: $violation" }
        }
    }
}
