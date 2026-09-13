import app.wlo.buildlogic.arch.ArchScopes
import app.wlo.buildlogic.arch.CheckArchitectureTask
import java.io.File

/**
 * `wlo.architecture-check` — registers `checkArchitecture` on the root project
 * (apply from the root build only). Rule data is captured in
 * `gradle.projectsEvaluated` (all projects configured) as plain strings; the
 * task action is filesystem + string work only (configuration-cache safe).
 *
 * Rules D1–D7 + D9 live in [app.wlo.buildlogic.arch.ArchRules]; D8 is a
 * documented review convention enforced via `WloResult` types (ARCHITECTURE §2.3).
 *
 * uiAtoms (WLO-0031 P2) — design-system enforcement for :feature:* and :app:
 * main sources. Severity via the `wlo.uiAtoms` Gradle property: `warn`
 * (default — prints counts + files, never fails, the P3 migration baseline)
 * or `enforce` (fails; the end state where a green build REQUIRES
 * conformance). Run: `./gradlew checkArchitecture -Pwlo.uiAtoms=enforce`.
 */
val checkArchitecture = tasks.register("checkArchitecture", CheckArchitectureTask::class)

gradle.projectsEvaluated {
    val root = gradle.rootProject

    // Two-step severity (WLO-0031 P2): warn during the migration waves,
    // enforce once P3 lands. Unknown values degrade to warn with a message.
    val uiAtomsSeverity: String =
        root.providers
            .gradleProperty("wlo.uiAtoms")
            .orElse(CheckArchitectureTask.MODE_WARN)
            .get()

    val edges = mutableListOf<String>()
    val commonMainDirs = mutableListOf<String>()
    val manifests = mutableListOf<String>()
    val uiSources = mutableListOf<String>()
    val engineDirs = mutableListOf<String>()
    val externalDeps = mutableListOf<String>()
    val paths = mutableListOf<String>()

    root.allprojects.forEach { project ->
        paths += project.path

        // D1/D2 — declared project-dependency edges on any configuration.
        project.configurations.forEach { configuration ->
            configuration.dependencies
                .withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                .forEach { dependency ->
                    edges += "${project.path}|${dependency.path}"
                }
            // D9 — declared external dependency coordinates (group|name).
            configuration.dependencies
                .withType(org.gradle.api.artifacts.ModuleDependency::class.java)
                .forEach { dependency ->
                    externalDeps += "${project.path}|${dependency.group}|${dependency.name}"
                }
        }

        val srcRoot = File(project.projectDir, "src")

        // D3 — commonMain source dirs.
        File(srcRoot, "commonMain/kotlin")
            .takeIf { it.isDirectory }
            ?.let { commonMainDirs += "${project.path}|${it.absolutePath}" }

        // D4 — module manifests anywhere under src/.
        if (srcRoot.isDirectory) {
            srcRoot.walkTopDown()
                .filter { it.isFile && it.name == "AndroidManifest.xml" }
                .forEach { manifests += "${project.path}|${it.absolutePath}" }
        }

        // D6 heuristic — UI-scoped projects only.
        if (ArchScopes.isUiScoped(project.path) && srcRoot.isDirectory) {
            srcRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { uiSources += "${project.path}|${it.absolutePath}" }
        }

        // D7 — every Kotlin source dir of the engines project.
        if (project.path == ArchScopes.ENGINES && srcRoot.isDirectory) {
            srcRoot.walkTopDown()
                .filter { it.isDirectory && it.name == "kotlin" }
                .forEach { engineDirs += "${project.path}|${it.absolutePath}" }
        }
    }

    checkArchitecture.configure {
        edgeLines.clear()
        edgeLines.addAll(edges)
        externalDepLines.clear()
        externalDepLines.addAll(externalDeps)
        commonMainDirLines.clear()
        commonMainDirLines.addAll(commonMainDirs)
        manifestLines.clear()
        manifestLines.addAll(manifests)
        uiSourceLines.clear()
        uiSourceLines.addAll(uiSources)
        engineDirLines.clear()
        engineDirLines.addAll(engineDirs)
        projectPathList = paths
        uiAtomsMode = uiAtomsSeverity

        // D4 merged-manifest backstop — :app registers `checkMergedManifest`
        // via `wlo.application`; only wire it when the task exists (a fixture
        // or future project named ":app" need not apply that convention).
        val appProject = root.findProject(":app")
        if (appProject != null && appProject.tasks.names.contains("checkMergedManifest")) {
            dependsOn(":app:checkMergedManifest")
        }
    }
}
