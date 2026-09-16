import app.wlo.buildlogic.arch.ArchScopes
import app.wlo.buildlogic.arch.ArchRules
import app.wlo.buildlogic.arch.CheckArchitectureTask
import app.wlo.buildlogic.arch.CheckResolvedEgressDependenciesTask
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
 * uiAtoms (WLO-0031) — design-system enforcement for :feature:* and :app:
 * main sources. Severity via the `wlo.uiAtoms` Gradle property: `enforce`
 * (default since the P3 migration landed — a green build REQUIRES
 * conformance) or `warn` (diagnostic baseline: prints counts + files, never
 * fails). Run: `./gradlew checkArchitecture` / `-Pwlo.uiAtoms=warn`.
 */
val checkArchitecture = tasks.register("checkArchitecture", CheckArchitectureTask::class)

gradle.projectsEvaluated {
    val root = gradle.rootProject

    // Two-step severity (WLO-0031 P2→P3): warn was the migration baseline,
    // enforce is the end state. Unknown values degrade to warn with a message.
    val uiAtomsSeverity: String =
        root.providers
            .gradleProperty("wlo.uiAtoms")
            .orElse(CheckArchitectureTask.MODE_ENFORCE)
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
        val resolvedEgressCheck =
            if (project.path != ":app" && project.path != ArchRules.D9_EGRESS_MODULE) {
                project.tasks.register("checkResolvedEgressDependencies", CheckResolvedEgressDependenciesTask::class)
                    .also { checkArchitecture.configure { dependsOn(it) } }
            } else {
                null
            }

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

            // D9 resolved-graph backstop. Only production compile/runtime
            // classpaths are relevant; test fixtures may legitimately bring
            // localhost servers. :app receives the restricted implementation
            // by design and :core:network owns the networking stack.
            val productionClasspath =
                configuration.isCanBeResolved &&
                    configuration.name.endsWith("compileClasspath", ignoreCase = true) &&
                    !configuration.name.contains("test", ignoreCase = true) &&
                    // AGP 9's standalone runtime/release consumers cannot disambiguate every
                    // artifact view published by a KMP Android library when
                    // resolved outside its normal build task. Debug carries
                    // main compile classpaths carry the production dependency graph and resolve with
                    // the attributes AGP supplies to its shipping compile.
                    !configuration.name.contains("release", ignoreCase = true)
            if (
                productionClasspath &&
                resolvedEgressCheck != null
            ) {
                resolvedEgressCheck.configure {
                    productionClasspaths.from(
                        configuration.incoming.artifactView {
                            // We inspect external modules only. Excluding project artifacts avoids
                            // introducing task cycles for AGP's synthetic self-dependencies while
                            // still traversing project edges to their external transitive modules.
                            componentFilter {
                                it is org.gradle.api.artifacts.component.ModuleComponentIdentifier
                            }
                            // The declared-edge D1/D2 gate owns broken or forbidden project edges;
                            // an unresolved project variant must not hide its clearer diagnostic.
                            lenient(true)
                            attributes.attribute(
                                org.gradle.api.attributes.Attribute.of("artifactType", String::class.java),
                                "jar",
                            )
                        }.files,
                    )
                }
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

        // D6 plus WLO-0063 lookalike checks — UI-scoped projects only.
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
