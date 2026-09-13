package app.wlo.buildlogic.arch

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The check cannot rot: for every rule (ARCHITECTURE.md §2.3 / ADR-005) a
 * fixture project applies `wlo.architecture-check`, introduces the violation,
 * and must FAIL with a message naming the rule; plus one clean fixture that
 * must PASS. Run via `./gradlew -p build-logic test` (also wired as CI job).
 */
class ArchitectureCheckSelfTest {

    private val buildLogicDir: File =
        File(System.getProperty("wlo.buildlogic.dir") ?: "build-logic").absoluteFile

    private fun fixture(files: Map<String, String>): File {
        val root = Files.createTempDirectory("wlo-arch-fixture").toFile()
        files.forEach { (path, content) ->
            val f = File(root, path)
            f.parentFile.mkdirs()
            f.writeText(content.replace("%BUILDLOGIC%", buildLogicDir.absolutePath))
        }
        return root
    }

    private fun settings(vararg includes: String): String = buildString {
        appendLine("pluginManagement {")
        appendLine("    includeBuild(\"%BUILDLOGIC%\")")
        appendLine("    repositories {")
        appendLine("        google()")

        appendLine("        mavenCentral()")
        appendLine("        gradlePluginPortal()")
        appendLine("    }")
        appendLine("}")
        appendLine("rootProject.name = \"fixture\"")
        includes.forEach { appendLine("include(\"$it\")") }
    }

    private val rootBuild = "build.gradle.kts" to "plugins { id(\"wlo.architecture-check\") }\n"

    @Test
    fun d1_featureCannotSeeRestrictedModule() {
        val result = runFailing(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":feature:f99-demo", ":core:network"),
                    rootBuild.first to rootBuild.second,
                    "feature/f99-demo/build.gradle.kts" to """
                        plugins { `java` }
                        dependencies { implementation(project(":core:network")) }
                    """.trimIndent(),
                    "core/network/build.gradle.kts" to "",
                ),
            ),
        )
        assertTrue("D1" in result.output, "output must name rule D1:\n${result.output}")
    }

    @Test
    fun d2_featureToFeatureEdgeForbidden() {
        val result = runFailing(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":feature:f01-a", ":feature:f02-b"),
                    rootBuild.first to rootBuild.second,
                    "feature/f01-a/build.gradle.kts" to """
                        plugins { `java` }
                        dependencies { implementation(project(":feature:f02-b")) }
                    """.trimIndent(),
                    "feature/f02-b/build.gradle.kts" to "",
                ),
            ),
        )
        assertTrue("D2" in result.output, "output must name rule D2:\n${result.output}")
    }

    @Test
    fun d2_selfEdgesAreExempt() {
        // AGP's own library test configurations declare a module as its own
        // project dependency; a self-edge is a tautology, not a coupling.
        val result = run(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":feature:f01-a"),
                    rootBuild.first to rootBuild.second,
                    "feature/f01-a/build.gradle.kts" to """
                        plugins { `java` }
                        dependencies { implementation(project(":feature:f01-a")) }
                    """.trimIndent(),
                ),
            ),
        )
        assertTrue("D2" !in result.output, "self-edges must not trip D2:\n${result.output}")
    }

    @Test
    fun d3_commonMainCannotImportAndroid() {
        val result = runFailing(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":core:evil"),
                    rootBuild.first to rootBuild.second,
                    "core/evil/build.gradle.kts" to "",
                    "core/evil/src/commonMain/kotlin/Bad.kt" to
                        "package core.evil\n\nimport android.view.View\n\nclass Bad : View(null)\n",
                ),
            ),
        )
        assertTrue("D3" in result.output, "output must name rule D3:\n${result.output}")
    }

    @Test
    fun d4_internetPermissionOutsideAppForbidden() {
        val manifest = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.INTERNET" />
            </manifest>
        """.trimIndent()
        val result = runFailing(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":core:leaky"),
                    rootBuild.first to rootBuild.second,
                    "core/leaky/build.gradle.kts" to "",
                    "core/leaky/src/main/AndroidManifest.xml" to manifest,
                ),
            ),
        )
        assertTrue("D4" in result.output, "output must name rule D4:\n${result.output}")
    }

    @Test
    fun d4_moduleManifestWithoutInternet_stillPasses() {
        // The positive half of D4 (INTERNET present in :app's MERGED manifest)
        // runs against the real AGP merge in `checkMergedManifest`; here we pin
        // the negative half's complement: plain library manifests are clean.
        val manifest = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" />
        """.trimIndent()
        val result = run(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":core:silent"),
                    rootBuild.first to rootBuild.second,
                    "core/silent/build.gradle.kts" to "",
                    "core/silent/src/main/AndroidManifest.xml" to manifest,
                ),
            ),
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":checkArchitecture")?.outcome,
            "a permission-free module manifest is D4-clean:\n${result.output}",
        )
    }

    @Test
    fun d6_rawDerivedValueRenderFails() {
        val result = runFailing(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":feature:f07-ui"),
                    rootBuild.first to rootBuild.second,
                    "feature/f07-ui/build.gradle.kts" to "",
                    "feature/f07-ui/src/main/kotlin/Raw.kt" to """
                        package feature.raw
                        fun render(trendValue: Double) = Unit
                        fun broken() = Text(trendValue.value)
                    """.trimIndent(),
                ),
            ),
        )
        assertTrue("D6" in result.output, "output must name rule D6:\n${result.output}")
    }

    @Test
    fun d7_enginesMustNotReadTheClock() {
        val result = runFailing(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":core:engines"),
                    rootBuild.first to rootBuild.second,
                    "core/engines/build.gradle.kts" to "",
                    "core/engines/src/commonMain/kotlin/Impure.kt" to """
                        package engines
                        fun nowish(): Long = Clock.System.now().toEpochMilliseconds()
                    """.trimIndent(),
                ),
            ),
        )
        assertTrue("D7" in result.output, "output must name rule D7:\n${result.output}")
    }

    @Test
    fun d9_ktorOutsideTheChokePointFails() {
        val result = runFailing(
            fixture(
                mapOf(
                    // A bare :app would break the merged-manifest backstop wiring,
                    // and the D9 rule is about ANY project declaring the stack.
                    "settings.gradle.kts" to settings(":core:leaky"),
                    rootBuild.first to rootBuild.second,
                    "core/leaky/build.gradle.kts" to """
                        plugins { `java` }
                        repositories { mavenCentral() }
                        dependencies { implementation("io.ktor:ktor-client-core:3.5.2") }
                    """.trimIndent(),
                ),
            ),
        )
        assertTrue("D9" in result.output, "output must name rule D9:\n${result.output}")
    }

    @Test
    fun d9_ktorAndOkhttpInsideCoreNetwork_areTheAllowedChokePoint() {
        val result = run(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":core:network"),
                    rootBuild.first to rootBuild.second,
                    "core/network/build.gradle.kts" to """
                        plugins { `java` }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation("io.ktor:ktor-client-core:3.5.2")
                            implementation("io.ktor:ktor-client-okhttp:3.5.2")
                            implementation("com.squareup.okhttp3:okhttp:5.5.0")
                            testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.0")
                        }
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":checkArchitecture")?.outcome,
            ":core:network is the single F12 §3.8 choke point — its stack must be legal:\n${result.output}",
        )
    }

    @Test
    fun d9_theAllowlistDoesNotExtendPastCoreNetwork() {
        val result = runFailing(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":feature:f99-ui"),
                    rootBuild.first to rootBuild.second,
                    "feature/f99-ui/build.gradle.kts" to """
                        plugins { `java` }
                        repositories { mavenCentral() }
                        dependencies { implementation("com.squareup.okhttp3:okhttp:5.5.0") }
                    """.trimIndent(),
                ),
            ),
        )
        assertTrue("D9" in result.output, "okhttp outside :core:network must still fail:\n${result.output}")
    }

    @Test
    fun d9_mockwebserverTestServersAreExempt() {
        val result = run(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":core:zoo"),
                    rootBuild.first to rootBuild.second,
                    "core/zoo/build.gradle.kts" to """
                        plugins { `java` }
                        repositories { mavenCentral() }
                        dependencies { testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.0") }
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":checkArchitecture")?.outcome,
            "mockwebserver binds localhost for tests — it cannot egress:\n${result.output}",
        )
    }

    @Test
    fun d9_okioStaysLegal() {
        val result = run(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":core:datastore"),
                    rootBuild.first to rootBuild.second,
                    "core/datastore/build.gradle.kts" to """
                        plugins { `java` }
                        repositories { mavenCentral() }
                        dependencies { implementation("com.squareup.okio:okio:3.18.2") }
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":checkArchitecture")?.outcome,
            "okio must remain legal (matched names are ktor/okhttp/retrofit):\n${result.output}",
        )
    }

    @Test
    fun d1_restrictedModuleSet_includesTheM6Vault() {
        // M6 (WLO-0028 PART A): :core:vault joined the D1 restricted set —
        // the data-driven rule must know it, so features cannot see the
        // vault impl even though it is a KMP core module.
        org.junit.Assert.assertTrue(
            "RESTRICTED_MODULES must contain :core:vault",
            app.wlo.buildlogic.arch.ArchRules.RESTRICTED_MODULES.contains(":core:vault"),
        )
        // ... and the machine rule still fires for it (not just :core:network).
        val result = runFailing(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":feature:f99-demo", ":core:vault"),
                    rootBuild.first to rootBuild.second,
                    "feature/f99-demo/build.gradle.kts" to """
                        plugins { `java` }
                        dependencies { implementation(project(":core:vault")) }
                    """.trimIndent(),
                    "core/vault/build.gradle.kts" to "",
                ),
            ),
        )
        assertTrue("D1" in result.output, "output must name rule D1:\n${result.output}")
    }

    @Test
    fun cleanFixturePasses() {
        val result = run(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":core:model"),
                    rootBuild.first to rootBuild.second,
                    "core/model/build.gradle.kts" to "",
                    "core/model/src/commonMain/kotlin/Fine.kt" to "package fine\n\nval fine = 1\n",
                ),
            ),
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":checkArchitecture")?.outcome,
            "clean fixture must pass:\n${result.output}",
        )
    }

    // ------------------------------------------------------------------
    // uiAtoms (WLO-0031 P2) — design-system enforcement, warn-by-default.

    /** A feature screen committing every banned uiAtoms pattern at once. */
    private val uiAtomsBadSource: String =
        """
        package feature.bad

        import androidx.compose.material3.Button
        import androidx.compose.material3.Card as M3Card
        import androidx.compose.material3.CardDefaults
        import androidx.compose.material3.*
        import androidx.compose.ui.graphics.Color
        import androidx.compose.ui.unit.dp

        val defaults = CardDefaults.cardColors(containerColor = Color.Black)

        fun fakeButton() = Button(onClick = {})
        fun aliasedCard() = M3Card {}
        fun surfaceClick() = Surface(
            onClick = {},
        ) {}
        val stroke = BorderStroke(1.dp, Color.Black)
        val receipt = FontFamily.Monospace
        val bigger = wloType.title.copy(fontSize = 20.sp)
        """.trimIndent()

    @Test
    fun uiAtoms_warnIsTheDefaultAndNeverFails() {
        val result = run(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":feature:f99-ui"),
                    rootBuild.first to rootBuild.second,
                    "feature/f99-ui/build.gradle.kts" to "",
                    "feature/f99-ui/src/main/kotlin/Bad.kt" to uiAtomsBadSource,
                ),
            ),
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":checkArchitecture")?.outcome,
            "warn mode must NEVER fail (migration baseline):\n${result.output}",
        )
        assertTrue("uiAtoms: WARN mode" in result.output, "must announce warn mode:\n${result.output}")
        assertTrue(
            "uiAtoms [banned-import material3.Button]:" in result.output,
            "must print the violation line format:\n${result.output}",
        )
    }

    @Test
    fun uiAtoms_enforceFailsTheBuild() {
        val result = runFailing(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":feature:f99-ui"),
                    rootBuild.first to rootBuild.second,
                    "feature/f99-ui/build.gradle.kts" to "",
                    "feature/f99-ui/src/main/kotlin/Bad.kt" to uiAtomsBadSource,
                ),
            ),
            extraArguments = listOf("-Pwlo.uiAtoms=enforce"),
        )
        assertTrue("mode=enforce" in result.output, "must name the mode:\n${result.output}")
        assertTrue(
            "uiAtoms [banned-import material3.Card]:" in result.output,
            "aliased banned imports must be caught:\n${result.output}",
        )
        assertTrue("uiAtoms [surface-onclick]:" in result.output, "Surface(onClick must be caught:\n${result.output}")
        assertTrue("uiAtoms [border-stroke]:" in result.output, "BorderStroke must be caught:\n${result.output}")
        assertTrue(
            "uiAtoms [fontfamily-monospace]:" in result.output,
            "FontFamily.Monospace must be caught:\n${result.output}",
        )
        assertTrue("uiAtoms [wlotype-copy]:" in result.output, "wloType.copy must be caught:\n${result.output}")
    }

    @Test
    fun uiAtoms_coreDesignsystemIsExempt() {
        // :core:designsystem is WHERE the sanctioned M3 usage lives — the scan
        // covers :feature:* and :app: only, even in enforce mode.
        val result = run(
            fixture(
                mapOf(
                    "settings.gradle.kts" to settings(":core:designsystem"),
                    rootBuild.first to rootBuild.second,
                    "core/designsystem/build.gradle.kts" to "",
                    "core/designsystem/src/main/kotlin/Bad.kt" to uiAtomsBadSource,
                ),
            ),
            extraArguments = listOf("-Pwlo.uiAtoms=enforce"),
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":checkArchitecture")?.outcome,
            "the design system itself must stay exempt:\n${result.output}",
        )
    }

    @Test
    fun uiAtoms_mainSourceSetsOnly_testsAreExempt() {
        val dir = Files.createTempDirectory("wlo-uiatoms-scan").toFile()
        val main = File(dir, "feature/f99-ui/src/main/kotlin").apply { mkdirs() }
        val unitTest = File(dir, "feature/f99-ui/src/test/kotlin").apply { mkdirs() }
        val mainFile = File(main, "Bad.kt").apply { writeText(uiAtomsBadSource) }
        val testFile = File(unitTest, "BadTest.kt").apply { writeText(uiAtomsBadSource) }
        val hits =
            app.wlo.buildlogic.arch.ArchRules.uiAtomViolations(
                mapOf(":feature:f99-ui" to listOf(mainFile.toPath(), testFile.toPath())),
            )
        assertTrue(hits.isNotEmpty(), "main-source violations must be found")
        assertTrue(
            hits.all { it.file.contains("/src/main/") },
            "only main source sets are scanned, got: ${hits.map { it.file }}",
        )
    }

    @Test
    fun uiAtoms_companionRulesAreNeverCaught() {
        val dir = Files.createTempDirectory("wlo-uiatoms-clean").toFile()
        val main = File(dir, "feature/f99-ui/src/main/kotlin").apply { mkdirs() }
        val fine = File(main, "Fine.kt")
        fine.writeText(
            """
            package feature.fine

            import androidx.compose.material3.CardDefaults
            import androidx.compose.material3.Surface
            import androidx.compose.material3.SwitchDefaults
            import androidx.compose.material3.Text

            fun well(color: androidx.compose.ui.graphics.Color) =
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8)) {
                    Text("tonal well", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                }

            val switchColors = SwitchDefaults.colors()
            val cardColors = CardDefaults.cardColors(containerColor = color)
            """.trimIndent(),
        )
        val hits =
            app.wlo.buildlogic.arch.ArchRules.uiAtomViolations(
                mapOf(":feature:f99-ui" to listOf(fine.toPath())),
            )
        assertTrue(
            hits.isEmpty(),
            "tonal wells + Defaults companions stay legal, got: ${hits.map { "${it.tag} ${it.file}:${it.line}" }}",
        )
    }

    private fun run(
        dir: File,
        extraArguments: List<String> = emptyList(),
    ): BuildResult =
        GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(listOf("checkArchitecture", "--stacktrace") + extraArguments)
            .build()

    private fun runFailing(
        dir: File,
        extraArguments: List<String> = emptyList(),
    ): BuildResult =
        GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(listOf("checkArchitecture", "--stacktrace") + extraArguments)
            .buildAndFail()
}
