package app.wlo.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.vault.BackupCodec
import app.wlo.core.vault.BackupContainer
import app.wlo.core.vault.BackupKdf
import app.wlo.core.vault.BackupOptions
import app.wlo.core.vault.LockTimeout
import app.wlo.core.vault.SnapshotAssembler
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * M6 screenshot sweep (rule-free, like the M3/M5 sweeps): walks the F12
 * consent shell + F13 vault surfaces on a seeded install and screencaps every
 * surface the review reads — dark, tnum, provenance everywhere, zero guilt.
 * Images land on /sdcard/m6-*.png and are pulled to /tmp/wlo-setup/ by the
 * operator. Rule-free on purpose: the compose test rule parks the app's frame
 * clock, which the delivery path's recomposition depends on; rule-free the
 * re-delivery is instant.
 */
@RunWith(AndroidJUnit4::class)
public class M6ScreensTest {
    private val koin get() = GlobalContext.get()

    private fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private var activeScenario: ActivityScenario<MainActivity>? = null

    private fun shot(name: String) {
        // Vault/gate surfaces hold FLAG_SECURE (discreet mode) — the OS
        // blanks screencaps of them (0-byte files). Drop the flag for the
        // capture instant and restore it afterwards; the flag ROUTING itself
        // is covered by M6FlagSecureWindowTest + M6HostileVaultTest.
        activeScenario?.onActivity { activity ->
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        SystemClock.sleep(250)
        OnboardingRobot.shell("screencap -p /sdcard/$name.png")
        activeScenario?.onActivity { activity ->
            app.wlo.core.vault
                .applyFlagSecure(activity, activity.currentDestinationForVerification ?: "")
        }
        SystemClock.sleep(150)
    }

    private fun deliver(uri: String) {
        val scenario =
            activeScenario
                ?: error("no active scenario")
        scenario.onActivity { activity ->
            activity.deliverNewIntentForVerification(
                Intent(activity, MainActivity::class.java).setData(Uri.parse(uri)),
            )
        }
    }

    private fun awaitRoute(
        expected: String,
        timeoutMs: Long = 20_000,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var actual: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            activeScenario?.onActivity { activity -> actual = activity.currentDestinationForVerification }
            if (actual == expected) return
            SystemClock.sleep(150)
        }
        error("route never became $expected (was $actual)")
    }

    private fun awaitText(
        text: String,
        timeoutMs: Long = 20_000,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (device().findObjects(By.textContains(text)).isNotEmpty()) return
            SystemClock.sleep(150)
        }
    }

    private fun tapTextContaining(text: String): Boolean {
        val node = device().findObjects(By.textContains(text)).firstOrNull() ?: return false
        node.click()
        return true
    }

    private fun scrollTo(text: String): Boolean {
        for (attempt in 0 until 8) {
            if (device().findObjects(By.textContains(text)).isNotEmpty()) return true
            device().swipe(540, 1700, 540, 500, 20)
            SystemClock.sleep(400)
        }
        return device().findObjects(By.textContains(text)).isNotEmpty()
    }

    @Test
    public fun captureM6Surfaces() {
        // Seed the full spec so receipts/counts carry real numbers (the
        // consent ledger rides along: 2 hash-chained grants).
        M6E2eSpec.seed()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activeScenario = scenario
        TestNav.awaitRoute(scenario, "hub")

        // --- settings (the IA §1 gear surface) -------------------------------
        deliver("wlo://settings")
        awaitRoute("app/settings")
        awaitText("App lock")
        SystemClock.sleep(500)
        shot("m6-settings")

        // --- AI studio: kill switch + six consent rows ------------------------
        deliver("wlo://ai/studio")
        awaitRoute("f12/studio")
        awaitText("Consent")
        SystemClock.sleep(600)
        shot("m6-studio")

        // --- consent sheet demo (point-of-use component) ----------------------
        tapTextContaining("Preview the consent sheet")
        awaitText("Keep it on-device")
        SystemClock.sleep(500)
        shot("m6-consent-demo")
        // No pressBack here: a back on a deep-link-landed stack exits the
        // activity. Deliveries return to known surfaces instead.
        deliver("wlo://ai/studio")
        awaitRoute("f12/studio")

        // --- BYOK shell + diagnostics section (studio, scrolled) -------------
        scrollTo("API key")
        SystemClock.sleep(400)
        shot("m6-byok")
        scrollTo("Report endpoint")
        SystemClock.sleep(400)
        shot("m6-diagnostics")

        // --- receipts viewer: one denied-egress receipt + chain verdict -------
        deliver("wlo://ai/receipts")
        awaitRoute("f12/receipts")
        awaitText("receipts")
        val egress = koin.get<app.wlo.core.ports.EgressPort>()
        runBlocking {
            egress.dispatch(
                app.wlo.core.ports.EgressRequest(
                    purpose = app.wlo.core.ports.EgressPurpose.FUTURE_CLOUD_CHAT,
                    host = "screenshots.invalid",
                    operation = "screenshot/lookup",
                    block = {},
                ),
            )
        }
        SystemClock.sleep(800)
        if (tapTextContaining("Verify chain")) SystemClock.sleep(900)
        SystemClock.sleep(400)
        shot("m6-receipts")

        // --- vault dashboard ---------------------------------------------------
        deliver("wlo://vault")
        awaitRoute("f13/vault")
        awaitText("Backup")
        SystemClock.sleep(600)
        shot("m6-vault")

        // --- backup controls ---------------------------------------------------
        tapTextContaining("Backup controls")
        awaitText("passphrase")
        SystemClock.sleep(500)
        shot("m6-backup")

        // --- restore wizard STAGED REPORT (a prepared backup file) ------------
        val backupName = buildBackupFixture()
        deliver("wlo://vault")
        awaitRoute("f13/vault")
        tapTextContaining("Restore from a backup file")
        awaitText("Choose backup file")
        tapTextContaining("Choose backup file")
        pickFixture(backupName)
        awaitText("Backup passphrase")
        // Click the field's label area (focuses the field), then type through
        // the shell input pipeline — the EditText a11y node appears lazily
        // and polling for it proved flaky; `input text` is deterministic.
        device().findObjects(By.textContains("Backup passphrase")).firstOrNull()?.click()
        SystemClock.sleep(500)
        OnboardingRobot.shell("input text ${M6E2eSpec.PASSPHRASE}")
        SystemClock.sleep(400)
        tapTextContaining("Validate")
        awaitText("rows total")
        SystemClock.sleep(600)
        shot("m6-restore-report")
        // Do NOT apply — the seed data stays (the walk continues elsewhere).

        // --- export wizard ------------------------------------------------------
        deliver("wlo://vault")
        awaitRoute("f13/vault")
        tapTextContaining("Export —")
        awaitText("JSON bundle")
        SystemClock.sleep(500)
        shot("m6-export")

        // --- CSV import mapping -------------------------------------------------
        val csvName = publishCsvFixture()
        deliver("wlo://vault")
        awaitRoute("f13/vault")
        tapTextContaining("Import —")
        awaitText("Choose a file")
        tapTextContaining("Choose a file")
        pickFixture(csvName)
        awaitText("map each column")
        SystemClock.sleep(700)
        shot("m6-csv-mapping")

        // --- app-lock gate (enable with IMMEDIATE timeout, background blip) ----
        val settings = koin.get<SettingsStore>()
        runBlocking {
            settings.setAppLockEnabled(true)
            settings.setLockTimeout(LockTimeout.IMMEDIATE.wireName)
        }
        device().pressHome()
        SystemClock.sleep(800)
        OnboardingRobot.shell("am start -n ${OnboardingRobot.targetPackage()}/app.wlo.app.MainActivity")
        awaitText("Turn app lock off")
        SystemClock.sleep(600)
        shot("m6-lockgate")
        // Restore: turn the lock off so later tests get an unlocked shell.
        tapTextContaining("Turn app lock off")
        SystemClock.sleep(800)

        scenario.onActivity { it.finish() }
        SystemClock.sleep(500)
    }

    /** Builds a REAL passphrase backup of the seed state into Download. */
    private fun buildBackupFixture(): String {
        val bytes =
            runBlocking {
                val assembler = koin.get<SnapshotAssembler>()
                val payload = assembler.assemble(BackupOptions())
                val json = BackupCodec.encode(payload, 1_760_000_000_000)
                val salt = ByteArray(BackupKdf.SALT_BYTES)
                BackupContainer.encrypt(json, BackupKdf.argon2idDefaults(salt), M6E2eSpec.PASSPHRASE.toCharArray())
            }
        return publishToDownload(bytes, "bck.wlo")
    }

    /** An openScale-flavored CSV for the import-mapping screenshot. */
    private fun publishCsvFixture(): String {
        val csv = "date,weight_kg,kcal_in\r\n2026-09-10,84.2,1800\r\n2026-09-11,84.0,1750\r\n"
        return publishToDownload(csv.toByteArray(), "csv.csv")
    }

    /**
     * MediaStore Download publish: app-writable on API 29 without
     * permissions, immediately visible to the system picker (no scan). The
     * name is uniquified — MediaStore dedupes collisions with "(n)" suffixes
     * and stale rows from previous installs are undeletable.
     */
    private fun publishToDownload(
        bytes: ByteArray,
        downloadName: String,
    ): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dot = downloadName.lastIndexOf('.')
        val uniqueName =
            downloadName.substring(0, dot) + "-" + (System.currentTimeMillis() % 1000000) + downloadName.substring(dot)
        val values =
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(android.provider.MediaStore.MediaColumns.SIZE, bytes.size)
            }
        val uri =
            context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore Downloads insert refused")
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        Thread.sleep(1_000)
        return uniqueName
    }

    /**
     * Drives the system document picker: roots drawer → Downloads root →
     * file (the picker may open on RECENT — never assume Downloads). The
     * drawer covers the file list and eats taps, so it is closed before any
     * file click.
     */
    private fun pickFixture(name: String) {
        val ui = device()
        val deadline = System.currentTimeMillis() + 60_000
        var picked = false
        var inDownloads = false
        while (System.currentTimeMillis() < deadline && !picked) {
            if (ui.hasObject(By.text("Open from"))) {
                ui.findObject(By.desc("Show roots"))?.click()
                Thread.sleep(900)
                continue
            }
            val file = ui.findObjects(By.textContains(name)).firstOrNull()
            if (file != null) {
                file.click()
                picked = true
                break
            }
            if (!inDownloads) {
                ui.findObject(By.desc("Show roots"))?.click()
                Thread.sleep(1_000)
                val entry =
                    ui.findObjects(By.text("Downloads")).firstOrNull { it.visibleCenter.x < 500 }
                if (entry != null) {
                    entry.click()
                    Thread.sleep(1_000)
                }
                if (ui.hasObject(By.textContains("FILES ON DOWNLOADS"))) {
                    inDownloads = true
                    // Grid view truncates file labels (~10 chars) — switch to
                    // the list view where full names render and textContains
                    // can match.
                    ui.findObject(By.desc("List view"))?.click()
                    Thread.sleep(1_000)
                }
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
                    .findObjects(By.textContains(" "))
                    .mapNotNull { it.text }
                    .filter { it.length in 2..40 }
                    .distinct()
                    .take(24)
            "picker never showed $name; on screen: $onScreen"
        }
        // Let the picker actually dismiss before the caller's awaits run.
        ui.wait(
            androidx.test.uiautomator.Until
                .gone(By.text("Open from")),
            10_000,
        )
        Thread.sleep(800)
    }
}
