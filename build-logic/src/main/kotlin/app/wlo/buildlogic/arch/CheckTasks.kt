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

    @get:Input
    public var projectPathList: List<String> = emptyList()

    @TaskAction
    public fun check() {
        val edges = edgeLines.map { line ->
            val parts = line.split('|')
            ProjectEdge(parts[0], parts[1])
        }
        val commonMainDirs = groupByProject(commonMainDirLines).mapValues { (_, v) -> v.map { Paths.get(it) } }
        val manifests = groupByProject(manifestLines).mapValues { (_, v) -> v.map { Paths.get(it) } }
        val uiFiles = groupByProject(uiSourceLines).mapValues { (_, v) -> v.map { Paths.get(it) } }
        val engineDirs = groupByProject(engineDirLines).mapValues { (_, v) -> v.map { Paths.get(it) } }

        val violations = buildList {
            addAll(ArchRules.dependencyViolations(edges))
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
            "checkArchitecture: OK — ${projectPathList.size} projects, D1–D7 clean " +
                "(${edges.size} dependency edges scanned).",
        )
    }

    private fun groupByProject(lines: Collection<String>): Map<String, List<String>> =
        lines.groupBy(
            keySelector = { it.substringBefore('|') },
            valueTransform = { it.substringAfter('|') },
        )
}

/**
 * D4 backstop: inspects :app's merged manifest output (debug variant) after
 * `processDebugMainManifest`. :app is entitled to INTERNET, so this task
 * verifies the merge ran and reports what the merged manifest contains; the
 * hard D4 ban lives in the module-manifest scan.
 */
public open class CheckMergedManifestTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public val mergedManifests: ConfigurableFileCollection = project.objects.fileCollection()

    @TaskAction
    public fun check() {
        val scanned = mergedManifests.files.filter { it.isFile }
        val withInternet = scanned.filter { it.readText().contains("android.permission.INTERNET") }
        logger.lifecycle(
            "checkMergedManifest: scanned ${scanned.size} merged manifest(s); " +
                "${withInternet.size} contain INTERNET (:app is the only allowed origin).",
        )
    }
}
