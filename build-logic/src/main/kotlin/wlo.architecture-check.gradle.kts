import app.wlo.buildlogic.arch.ArchScopes
import app.wlo.buildlogic.arch.CheckArchitectureTask
import java.io.File

/**
 * `wlo.architecture-check` — registers `checkArchitecture` on the root project
 * (apply from the root build only). Rule data is captured in
 * `gradle.projectsEvaluated` (all projects configured) as plain strings; the
 * task action is filesystem + string work only (configuration-cache safe).
 *
 * Rules D1–D7 live in [app.wlo.buildlogic.arch.ArchRules]; D8 is a documented
 * review convention for M1 (ADR-005).
 */
val checkArchitecture = tasks.register("checkArchitecture", CheckArchitectureTask::class)

gradle.projectsEvaluated {
    val root = gradle.rootProject

    val edges = mutableListOf<String>()
    val commonMainDirs = mutableListOf<String>()
    val manifests = mutableListOf<String>()
    val uiSources = mutableListOf<String>()
    val engineDirs = mutableListOf<String>()
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
        commonMainDirLines.clear()
        commonMainDirLines.addAll(commonMainDirs)
        manifestLines.clear()
        manifestLines.addAll(manifests)
        uiSourceLines.clear()
        uiSourceLines.addAll(uiSources)
        engineDirLines.clear()
        engineDirLines.addAll(engineDirs)
        projectPathList = paths

        // D4 merged-manifest backstop — :app registers `checkMergedManifest`
        // via `wlo.application`; only wire it when the project exists (the
        // TestKit fixtures are plain builds).
        if (root.findProject(":app") != null) {
            dependsOn(":app:checkMergedManifest")
        }
    }
}
