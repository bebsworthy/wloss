package app.wlo.app

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.vault.BackupManager
import app.wlo.core.vault.SafBackupStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M6 PART A acceptance, LEG 1 (the seeder): seeds the [M6E2eSpec] data
 * through the REAL repositories in the app's own Koin graph, then runs a REAL
 * backup — passphrase-encrypted (R-U5), assembled from the live stores,
 * written through the SAF Documents folder chosen via the REAL system picker
 * (UiAutomator completes the dialog the user would), rotated keep-7 (R-U5).
 *
 * The backup bytes are ALSO mirrored to app-private `files/e2e/` so the host
 * script can pull them via `run-as` — after the uninstall in leg 2, that
 * mirror is gone too, which is the point: the only survivor is the file the
 * HOST holds (a lost-phone recovery in miniature).
 */
@RunWith(AndroidJUnit4::class)
public class M6SeederBackupTest {
    @Test
    public fun seed_runBackupNow_toSaFDocuments() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext.applicationContext as Context
        val koin = GlobalContext.get()
        val device = UiDevice.getInstance(instrumentation)

        // 1. Seed the exact M6E2eSpec state (profile/measurements/diary/
        //    revisions/food/list/pantry/recipe/targets/consent ledger).
        M6E2eSpec.seed()

        // 2. Choose the backup folder through the REAL picker flow, seeded to
        //    the emulator's Documents directory.
        val documentsTree: Uri =
            android.provider.DocumentsContract
                .buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity -> activity.pickBackupFolder(documentsTree) }

        // The system folder dialog: API 30+ shows "Use this folder"; API 29's
        // tree picker opens on Downloads with an `ALLOW ACCESS TO "…"` bar and
        // then a CANCEL/ALLOW confirm dialog. (Documented deviation: the API
        // 29 emulator's picker lands on Downloads — the MECHANISM under test
        // is the real SAF pick + persistable grant, and the folder choice is
        // the user's at runtime.)
        val accepted =
            clickAny(device, listOf("Use this folder", "USE THIS FOLDER")) ||
                (clickPrefix(device, "ALLOW ACCESS TO") && clickAny(device, listOf("ALLOW")))
        check(accepted) { "SAF folder picker never confirmed (is the emulator showing the tree dialog?)" }

        // 3. Wait for the persisted grant + uri.
        val settings = koin.get<SettingsStore>()
        val folderUri = waitUntil(15_000) { runBlocking { settings.backupFolderUriOnce() } }
        assertNotNull(folderUri, "backup folder uri must persist after the pick")

        // 4. The REAL backup: passphrase path (R-U5: encrypted by default).
        val manager = koin.get<BackupManager>()
        val outcome =
            runBlocking {
                manager.backupNow(destination = folderUri!!, passphrase = M6E2eSpec.PASSPHRASE.toCharArray())
            }
        assertTrue(outcome.fileName.startsWith("wlo_backup_"), "rotation-contract name: ${outcome.fileName}")
        assertTrue(outcome.sizeBytes > 100, "a real backup is not empty")

        // 5. The destination really holds it (SAF round-trip).
        val store = SafBackupStore(appContext, folderUri!!)
        val listed = runBlocking { store.list() }
        assertTrue(listed.any { it.name == outcome.fileName })

        // 6. Mirror the bytes to app-private storage for the host script's
        //    run-as pull (leg 2 restores from the HOST copy after wipe).
        val mirrored = runBlocking { store.read(listed.first { it.name == outcome.fileName }) }
        val export = File(appContext.filesDir, "e2e/backup-export.wlo")
        export.parentFile!!.mkdirs()
        export.writeBytes(mirrored)

        println("M6E2E seeder: backup=${outcome.fileName} bytes=${outcome.sizeBytes} uri=$folderUri")
        val profiles = runBlocking { koin.get<app.wlo.core.database.WloDatabase>().profiles().all() }
        assertEquals(M6E2eSpec.EXPECT_PROFILES, profiles.size)
    }

    private fun clickAny(
        device: UiDevice,
        texts: List<String>,
    ): Boolean {
        for (text in texts) {
            if (device.hasObject(By.text(text))) {
                device.findObjects(By.text(text)).firstOrNull()?.click()
                device.waitForIdle(2_000)
                SystemClock.sleep(1_000)
                return true
            }
        }
        return false
    }

    private fun clickPrefix(
        device: UiDevice,
        prefix: String,
    ): Boolean {
        if (device.hasObject(By.textContains(prefix))) {
            device.findObjects(By.textContains(prefix)).firstOrNull()?.click()
            device.waitForIdle(2_000)
            SystemClock.sleep(1_000)
            return true
        }
        return false
    }

    private fun <T> waitUntil(
        timeoutMs: Long,
        probe: () -> T?,
    ): T? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            probe()?.let { return it }
            SystemClock.sleep(500)
        }
        return probe()
    }
}
