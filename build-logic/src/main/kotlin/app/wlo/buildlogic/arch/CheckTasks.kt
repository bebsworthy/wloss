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
 */
public open class CheckArchitectureTask : DefaultTask() {

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
        logger.lifecycle(
            "checkArchitecture: OK — ${projectPathList.size} projects, D1–D7 + D9 clean " +
                "(${edges.size} dependency edges, ${externalDeps.size} external artifacts scanned).",
        )
    }

    private fun groupByProject(lines: Collection<String>): Map<String, List<String>> =
        lines.groupBy(
            keySelector = { it.substringBefore('|') },
            valueTransform = { it.substringAfter('|') },
        )
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
    public val mergedManifests: ConfigurableFileCollection = project.objects.fileCollection()

    @TaskAction
    public fun check() {
        val scanned = mergedManifests.files.filter { it.isFile && it.name.endsWith(".xml") }
        if (scanned.isEmpty()) {
            throw GradleException(
                "D4: no merged manifest found among ${mergedManifests.files.size} output(s) — " +
                    "the manifest merge must run before this check.",
            )
        }
        val withInternet = scanned.filter { it.readText().contains("android.permission.INTERNET") }
        if (withInternet.isEmpty()) {
            throw GradleException(
                "D4/R-S13: :app's merged manifest does not declare android.permission.INTERNET. " +
                    "All egress flows through :core:network's NetworkDispatcher (receipted, consent-gated) " +
                    "and needs the permission in :app — and ONLY :app (module manifests are banned).",
            )
        }
        logger.lifecycle(
            "checkMergedManifest: ${scanned.size} merged manifest(s) scanned; INTERNET present in :app " +
                "(and only :app — the module-manifest scan bans it elsewhere).",
        )
    }
}
