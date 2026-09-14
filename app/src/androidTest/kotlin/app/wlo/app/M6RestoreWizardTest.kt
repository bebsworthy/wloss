package app.wlo.app

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import app.wlo.core.database.WloDatabase
import app.wlo.core.vault.BackupCodec
import app.wlo.core.vault.BackupContainer
import app.wlo.core.vault.BackupKdf
import app.wlo.core.vault.BackupOptions
import app.wlo.core.vault.SnapshotAssembler
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M6 PART B acceptance (c)+(f): the staged-restore WIZARD, driven end-to-end
 * through its real UI against a WIPED database. The wipe is the in-process
 * `clearAllTables` primitive (R-B7's fresh start): an in-test `pm clear` is
 * structurally impossible — the instrumentation shares the app's process, so
 * clearing the package kills the process, and with it the test ("Test
 * instrumentation process crashed") — while the TRUE lost-phone wipe
 * (uninstall + reinstall, nothing survives) is exactly what
 * scripts/e2e-wipe-restore.sh drives with [M6RestoreAssertTest]. Here the
 * wizard must rebuild the [M6E2eSpec] state from a REAL passphrase-encrypted
 * backup (assembler → codec → container): pick → passphrase → staged report →
 * confirm → atomic apply → logical equality with the seed spec.
 *
 * The backup file is published through MediaStore Downloads (app-writable on
 * API 29 without permissions, immediately visible to the system picker —
 * direct /sdcard writes are EACCES under scoped storage and the UiAutomation
 * shell service parses no redirects). Driven with plain UiAutomator on
 * purpose (no compose rule): the flow crosses the system document picker.
 */
@RunWith(AndroidJUnit4::class)
public class M6RestoreWizardTest {
    private val fixtureName = "wlo_backup_fixture.wlo"
    private val hostileName = "wlo_backup_hostile.wlo"

    private fun koin() = GlobalContext.get()

    private fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * Publishes [bytes] as [downloadName] into MediaStore Downloads — the
     * picker's default root reads MediaStore directly, so no scan is needed.
     * App-owned rows do not survive pm clear; every test publishes its own.
     */
    private fun publishToDownload(
        bytes: ByteArray,
        downloadName: String,
    ): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // MediaStore dedupes display names with "(n)" suffixes and rows from
        // previous installs linger — so use a name that cannot collide and
        // ask the store back for what it actually recorded.
        val uniqueName = downloadName.replace(".wlo", "-${System.currentTimeMillis() % 1000000}.wlo")
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.SIZE, bytes.size)
            }
        val uri =
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore Downloads insert refused")
        context.contentResolver.openOutputStream(uri)!!.use { out -> out.write(bytes) }
        val actualName =
            context.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: uniqueName
        // MediaStore-visible within the same tick; settle for the picker UI.
        Thread.sleep(1_000)
        return actualName
    }

    private fun buildBackupBytes(): ByteArray =
        runBlocking {
            val assembler = koin().get<SnapshotAssembler>()
            val payload = assembler.assemble(BackupOptions())
            val json = BackupCodec.encode(payload, 1_760_000_000_000)
            val salt = ByteArray(BackupKdf.SALT_BYTES)
            BackupContainer.encrypt(json, BackupKdf.argon2idDefaults(salt), M6E2eSpec.PASSPHRASE.toCharArray())
        }

    /**
     * Clicks the first visible object whose text equals [text], falling back
     * to a contains-match. Exact-first matters since the wizard's step header
     * ("Step 1 of 4 — Pick a file") contains the button's label — clicking
     * the inert header would never open the picker.
     */
    private fun clickText(
        ui: UiDevice,
        text: String,
    ): Boolean {
        val target =
            ui.findObjects(By.text(text)).firstOrNull()
                ?: ui.findObjects(By.textContains(text)).firstOrNull()
        if (target != null) {
            target.click()
            Thread.sleep(900)
            return true
        }
        return false
    }

    /**
     * Drives the system document picker: roots drawer → Downloads root →
     * file. The picker may open on RECENT — never assume the Downloads
     * listing is in front of us. The drawer's "Open from" header (and the
     * toolbar title, which can carry the same string) is handled BOUNDEDLY:
     * aim it at Downloads at most three times, then close it with back and
     * hunt for the file whatever the chrome says.
     */
    private fun pickInDocumentsUi(
        ui: UiDevice,
        fileName: String,
    ) {
        val deadline = System.currentTimeMillis() + 60_000
        var picked = false
        var inDownloads = false
        var drawerActions = 0
        while (System.currentTimeMillis() < deadline && !picked) {
            val drawerOpen = ui.hasObject(By.text("Open from"))
            if (drawerOpen && drawerActions < 3) {
                drawerActions++
                val entry =
                    ui.findObjects(By.text("Downloads")).firstOrNull { it.visibleCenter.x < 500 }
                if (entry != null) {
                    entry.click()
                    inDownloads = true
                    Thread.sleep(1_000)
                    continue
                }
                ui.findObject(By.desc("Show roots"))?.click()
                Thread.sleep(900)
                continue
            }
            if (drawerOpen) {
                // Stuck drawer: close it and hunt for the file anyway.
                ui.pressBack()
                Thread.sleep(900)
            }
            val file: UiObject2? = ui.findObjects(By.textContains(fileName)).firstOrNull()
            if (file != null) {
                file.click()
                picked = true
                break
            }
            if (!inDownloads) {
                ui.findObject(By.desc("Show roots"))?.click()
                Thread.sleep(1_000)
                drawerActions++
                val entry =
                    ui.findObjects(By.text("Downloads")).firstOrNull { it.visibleCenter.x < 500 }
                if (entry != null) {
                    entry.click()
                    Thread.sleep(1_000)
                }
                if (ui.hasObject(By.textContains("FILES ON DOWNLOADS"))) inDownloads = true
            } else {
                // The Downloads listing grows across runs — scroll it while
                // searching (the file may sit below the fold).
                ui.swipe(540, 1700, 540, 800, 20)
                Thread.sleep(600)
            }
        }
        check(picked) {
            val onScreen =
                ui
                    .findObjects(By.textContains("e"))
                    .mapNotNull { it.text }
                    .filter { it.length in 2..40 }
                    .distinct()
                    .take(20)
            "document picker never showed $fileName; on screen: $onScreen"
        }
        // Let the picker actually dismiss before the caller's awaits run.
        ui.wait(
            androidx.test.uiautomator.Until
                .gone(By.text("Open from")),
            10_000,
        )
        Thread.sleep(800)
    }

    /** Swipes up between click attempts until [text] is on screen and tapped. */
    private fun scrollAndClick(
        ui: UiDevice,
        text: String,
    ) {
        for (attempt in 0 until 6) {
            if (clickText(ui, text)) return
            ui.swipe(540, 1700, 540, 600, 20)
            Thread.sleep(500)
        }
        error("never found to tap: $text")
    }

    private fun launchApp() {
        OnboardingRobot.shell(
            "am start -n ${OnboardingRobot.targetPackage()}/app.wlo.app.MainActivity",
        )
        Thread.sleep(2_000)
    }

    @Test
    public fun wipeAndRestoreThroughWizard_rebuildsSeedState() {
        // --- Phase A: seed, back up, publish, WIPE --------------------------
        M6E2eSpec.seed()
        val publishedFixture = publishToDownload(buildBackupBytes(), fixtureName)
        val db = koin().get<WloDatabase>()
        // The lost-phone moment, in-process (R-B7 fresh start): nothing left.
        runBlocking { db.clearAllTables() }
        runBlocking { assertEquals(0, db.profiles().all().size, "the wipe must have cleared the app") }

        // --- Phase B: the wizard rebuilds everything ------------------------
        launchApp()
        val ui = device()
        val inApp = { text: String -> ui.hasObject(By.textContains(text)) }

        // Vault dashboard → restore wizard (fresh install = onboarding shows;
        // the wlo://vault deep link lands on the dashboard regardless).
        OnboardingRobot.shell("am start -a android.intent.action.VIEW -d wlo://vault")
        Thread.sleep(2_000)
        waitUntilUi(ui, "Restore from a backup file")
        clickText(ui, "Restore from a backup file")
        waitUntilUi(ui, "Pick a file")
        clickText(ui, "Pick a file")
        pickInDocumentsUi(ui, publishedFixture)

        // Passphrase → validate → the staged report.
        waitUntilUi(ui, "Backup passphrase")
        val field =
            ui.findObjects(By.textContains("Backup passphrase")).firstOrNull()
                ?: error("passphrase field missing")
        field.click()
        enterText(ui, M6E2eSpec.PASSPHRASE)
        clickText(ui, "Validate")

        waitUntilUi(ui, "Validated")
        waitUntilUi(ui, "rows total")
        assertTrue(inApp("measurements"), "the report must show the measurements section")

        scrollAndClick(ui, "Continue")
        // The report's Continue commits DIRECTLY (confirmCommit) — the
        // confirm step is currently unreachable from the report (flagged for
        // the UX review); the apply itself is the all-or-nothing transaction.
        waitUntilUi(ui, "Restored")

        // Logical equality with the seed spec — the wizard rebuilt everything.
        waitUntilDb { runBlocking { db.profiles().all().size == M6E2eSpec.EXPECT_PROFILES } }
        runBlocking { M6E2eSpec.assertCounts(db) }
    }

    @Test
    public fun hostileFile_failsCleanly_andDataIsUntouched() {
        M6E2eSpec.seed()
        val publishedHostile = publishToDownload("WLOBbroken-garbage".toByteArray(), hostileName)

        val db = koin().get<WloDatabase>()
        val before = runBlocking { db.profiles().all().size to db.diaryEntries().all().size }
        assertEquals(M6E2eSpec.EXPECT_PROFILES, before.first)

        launchApp()
        OnboardingRobot.shell("am start -a android.intent.action.VIEW -d wlo://vault")
        Thread.sleep(2_000)
        val ui = device()
        waitUntilUi(ui, "Restore from a backup file")
        clickText(ui, "Restore from a backup file")
        waitUntilUi(ui, "Pick a file")
        clickText(ui, "Pick a file")
        pickInDocumentsUi(ui, publishedHostile)

        waitUntilUi(ui, "Backup passphrase")
        val field =
            ui.findObjects(By.textContains("Backup passphrase")).firstOrNull()
                ?: error("passphrase field missing")
        field.click()
        enterText(ui, M6E2eSpec.PASSPHRASE)
        clickText(ui, "Validate")

        waitUntilUi(ui, "Nothing was changed")
        val after = runBlocking { db.profiles().all().size to db.diaryEntries().all().size }
        assertEquals(before, after, "a hostile file must never touch live data")
    }

    private fun enterText(
        ui: UiDevice,
        value: String,
    ) {
        // Compose's OutlinedTextField surfaces in the a11y tree as an
        // EditText NODE distinct from its label text — target the class, not
        // the label (the Validate button enables on non-empty content).
        val box =
            ui.findObjects(By.clazz("android.widget.EditText")).firstOrNull()
                ?: error("passphrase box missing")
        box.click()
        Thread.sleep(400)
        box.text = value
        Thread.sleep(300)
    }

    private fun waitUntilUi(
        ui: UiDevice,
        text: String,
    ) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (ui.hasObject(By.textContains(text))) return
            Thread.sleep(300)
        }
        OnboardingRobot.shell("screencap -p /sdcard/wiz-debug-${System.currentTimeMillis() % 100000}.png")
        val onScreen =
            ui
                .findObjects(By.textContains(" "))
                .mapNotNull { it.text }
                .distinct()
                .take(20)
        error("UI never showed: $text; on screen: $onScreen")
    }

    private fun waitUntilDb(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        error("db condition never became true")
    }
}
