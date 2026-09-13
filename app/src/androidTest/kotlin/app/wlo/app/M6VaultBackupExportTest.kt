package app.wlo.app

import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.ports.DataVaultPort
import app.wlo.core.vault.CsvTable
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M6 PART B acceptance (b)+(d): the backup CONTROLS driven through the real
 * SAF folder pick (the [M6SeederBackupTest] technique) + passphrase setup +
 * backup-now, with the dashboard's last-backup state updating; and the CSV
 * EXPORT whose parsed row count equals the seeded measurement count.
 */
@RunWith(AndroidJUnit4::class)
public class M6VaultBackupExportTest {
    @get:Rule
    public val rule = createAndroidComposeRule<MainActivity>()

    private fun koin() = GlobalContext.get()

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            // The compose rule parks the app's frame clock between test
            // actions; pump it so pending recompositions can run.
            runCatching {
                rule.mainClock.autoAdvance = false
                rule.mainClock.advanceTimeBy(200)
                rule.mainClock.autoAdvance = true
            }
            if (condition()) return
            Thread.sleep(200)
        }
        val texts =
            OnboardingRobot
                .device()
                .findObjects(
                    androidx.test.uiautomator.By
                        .textContains(" "),
                ).mapNotNull { it.text }
                .filter { it.length in 3..60 }
                .distinct()
                .take(26)
        val failureNode =
            rule
                .onAllNodesWithTag("f13-backup-failure", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()
        val failureText =
            failureNode?.let { node ->
                runCatching {
                    node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
                }.getOrNull()
            }
        error("condition never became true; failureNode=[$failureText]; on screen: $texts")
    }

    private fun openVaultScreen(
        deepLink: String,
        route: String,
        tag: String,
    ) {
        // Pumping delivery: the compose rule parks the app's frame clock
        // between test actions, so the intent's LaunchedEffect-driven
        // navigation needs an explicit clock pump (see TestNav).
        TestNav.deliverPumpingClock(rule, deepLink, route)
        TestNav.awaitTag(rule, tag)
    }

    private fun acceptSaFFolderDialog(): Boolean {
        val ui = OnboardingRobot.device()

        // API 30+: "Use this folder". API 29: the tree picker opens on
        // Downloads with an ALLOW ACCESS bar, then a CANCEL/ALLOW dialog.
        fun clickAny(texts: List<String>): Boolean {
            for (text in texts) {
                val target = ui.findObjects(By.text(text)).firstOrNull()
                if (target != null) {
                    target.click()
                    ui.waitForIdle(2_000)
                    SystemClock.sleep(1_000)
                    return true
                }
            }
            return false
        }
        val accepted =
            clickAny(listOf("Use this folder", "USE THIS FOLDER")) ||
                (
                    run {
                        val bar = ui.findObjects(By.textContains("ALLOW ACCESS TO")).firstOrNull()
                        if (bar != null) {
                            bar.click()
                            ui.waitForIdle(2_000)
                            SystemClock.sleep(1_000)
                        }
                        bar != null
                    } &&
                        clickAny(listOf("ALLOW"))
                )
        return accepted
    }

    @Test
    public fun backupControls_folderPassphraseNow_dashboardShowsLastBackup() {
        M6E2eSpec.seed()

        // --- backup controls ---------------------------------------------------
        openVaultScreen("wlo://vault", "f13/vault", "f13-vault")
        rule.onNodeWithTag("f13-open-backup", useUnmergedTree = true).performClick()
        TestNav.awaitRoutePumpingClock(rule, "f13/vault/backup-setup")
        TestNav.awaitTag(rule, "f13-backup-title")

        // 1. Folder through the REAL system picker. The screen's own
        // "Choose folder" button launches SAF with a null initial uri, which
        // on the API 29 tree picker lands on a bare listing with no select
        // bar; the activity's launcher with the Documents tree (the M6Seeder
        // technique) opens it with the ALLOW bar visible — same real flow.
        // The Documents directory itself must exist (MediaProvider drops the
        // empty dir on clears) or the tree resolves to nothing.
        OnboardingRobot.shell("mkdir -p /sdcard/Documents")
        val documentsTree: Uri =
            android.provider.DocumentsContract
                .buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
        rule.activityRule.scenario.onActivity { activity -> activity.pickBackupFolder(documentsTree) }
        waitUntil { acceptSaFFolderDialog() }
        val settings = koin().get<SettingsStore>()
        waitUntil { runBlocking { settings.backupFolderUriOnce() != null } }

        // 2. Passphrase (armed auto-backup key rides it).
        rule.onNodeWithTag("f13-passphrase-field", useUnmergedTree = true).performTextInput(M6E2eSpec.PASSPHRASE)
        rule.onNodeWithTag("f13-set-passphrase", useUnmergedTree = true).performClick()
        waitUntil {
            rule
                .onAllNodesWithText("Passphrase: set", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // 3. Backup now → outcome line + rotation listing. The button sits
        // below the fold — the rule's click injects a gesture at the node's
        // on-screen coordinates, so bring the row into view first.
        OnboardingRobot.device().swipe(540, 1700, 540, 700, 20)
        Thread.sleep(600)
        rule.onNodeWithTag("f13-backup-now", useUnmergedTree = true).performClick()
        waitUntil {
            rule
                .onAllNodesWithTag("f13-backup-outcome", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        waitUntil {
            rule
                .onAllNodesWithTag("f13-files-empty", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }

        // 4. Dashboard: last-backup state now names the rotated file.
        TestNav.deliverPumpingClock(rule, "wlo://vault", "f13/vault")
        waitUntil {
            rule
                .onAllNodesWithText("wlo_backup_", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    public fun exportCsv_parsedRowsMatchSeed() {
        M6E2eSpec.seed()

        // The rendered CSV (the exact artifact the wizard writes) parses to
        // exactly the seeded measurement events.
        val vault = koin().get<DataVaultPort>()
        val render = runBlocking { vault.renderMetricCsv() }
        val db = koin().get<app.wlo.core.database.WloDatabase>()
        val dbKinds =
            runBlocking {
                db.measurementEvents().all().joinToString { "${it.kind}:${it.dayEpochDay}:${it.valueReal}" }
            }
        assertEquals(
            M6E2eSpec.EXPECT_MEASUREMENTS,
            render.dataRows,
            "csv rows vs db events [$dbKinds] csv=[${render.csv.take(400)}]",
        )
        val table = CsvTable.parse(render.csv)
        assertEquals(M6E2eSpec.EXPECT_MEASUREMENTS, table.rows.size)
        assertTrue(table.header.contains("day"))
        assertTrue(table.header.contains("weight_kg"))

        // And the export surface previews the same count.
        openVaultScreen("wlo://vault", "f13/vault", "f13-vault")
        rule.onNodeWithTag("f13-open-export", useUnmergedTree = true).performClick()
        TestNav.awaitRoutePumpingClock(rule, "f13/vault/export")
        TestNav.awaitTag(rule, "f13-export-title")
        rule.onNodeWithTag("f13-format-csv", useUnmergedTree = true).performClick()
        waitUntil {
            rule
                .onAllNodesWithText("3 data rows", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
