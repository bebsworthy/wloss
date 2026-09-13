package app.wlo.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.wlo.core.ai.OkioZooManager
import app.wlo.core.ai.ZooManager
import app.wlo.core.ai.ZooManifest
import app.wlo.core.ai.ZooModelState
import app.wlo.core.network.EgressLedger
import app.wlo.core.ports.EgressPort
import app.wlo.core.ports.EgressPurpose
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M4 PART A acceptance on REAL egress (acceptance 3 + 5): the zoo's first
 * entry (ADR-007 food classifier, 9.4 MB from the pinned Hugging Face URL)
 * downloads ONCE through the NetworkDispatcher into the app's real storage,
 * hash-verifies, still verifies with the emulator's radio OFF, and the debug
 * egress monitor (wlo://debug/egress) is rendered reading exactly one zoo
 * receipt from the PERSISTED ledger.
 *
 * Deliberately NOT a compose-rule test: the ~1-minute download blocks the
 * instrumentation thread and the rule's scenario close then times out on
 * DESTROYED (the M1 note, twice over). ActivityScenario + the route hook is
 * the M3-proven shape for deep-link-driven flows; the monitor's rendering is
 * evidenced by a screencap (M3ScreensTest pattern) + its data source asserted
 * from the ledger itself.
 *
 * Airplane mode on the API 29 emulator (the incantation that works, verified
 * on wlo-api29):
 *   `adb shell cmd connectivity airplane-mode enable`   (cut: ping → unreachable)
 *   `adb shell cmd connectivity airplane-mode disable`  (restore)
 * issued through the instrumentation's UiAutomation (shell UID on emulator).
 */
@RunWith(AndroidJUnit4::class)
public class M4ZooEgressE2eTest {
    private val modelId: String = "food-classifier/1"

    @Test
    public fun zooDownloadsOnce_verifiesInAirplaneMode_monitorShowsOneZooReceipt() {
        val koin = GlobalContext.get()
        val zoo = koin.get<ZooManager>()
        val manifest = koin.get<ZooManifest>()
        val ledger = koin.get<EgressLedger>()
        val egress = koin.get<EgressPort>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val model = checkNotNull(manifest.byId(modelId)) { "manifest must pin the food classifier" }

            // 1. Size disclosure BEFORE download (the manifest is bundled+offline).
            assertEquals(ADR007_SHA256, model.sha256Hex, "the bundle pins the ADR-007 hash")
            assertEquals(ADR007_SIZE_BYTES, model.sizeBytes)

            // 2. The real 9.4 MB download through the dispatcher (receipted).
            val handle = runBlocking { zoo.ensureAvailable(modelId).getOrThrow() }
            assertEquals(ADR007_SHA256, handle.sha256Hex, "installed artifact must match the pin")
            assertEquals(ADR007_SIZE_BYTES, handle.sizeBytes)
            assertEquals(ZooModelState.DownloadedVerified::class, zoo.state(modelId)::class)
            assertEquals(1L, runBlocking { zooReceipts(ledger) }, "exactly one zoo receipt after the first download")

            // 3. Download-once: the second call must not add a receipt.
            runBlocking { zoo.ensureAvailable(modelId).getOrThrow() }
            assertEquals(1L, runBlocking { zooReceipts(ledger) }, "verified files never re-download (R-S14)")

            // 4. Airplane mode ON; a FRESH zoo manager over the same storage must
            // verify from disk (startup hash) and never touch the dead radio.
            shell(device, "cmd connectivity airplane-mode enable")
            try {
                assertTrue(awaitAirplaneMode(device, enabled = true), "airplane mode must engage")
                val offlineZoo =
                    OkioZooManager(
                        manifest = manifest,
                        egress = egress,
                        fileSystem = FileSystem.SYSTEM,
                        baseDir = zooBaseDir(),
                    )
                val offlineHandle = runBlocking { offlineZoo.ensureAvailable(modelId).getOrThrow() }
                assertEquals(ADR007_SHA256, offlineHandle.sha256Hex, "airplane-mode-after: verification holds")
                assertEquals(1L, runBlocking { zooReceipts(ledger) }, "no new receipt — nothing left the device")
            } finally {
                shell(device, "cmd connectivity airplane-mode disable")
                assertTrue(awaitAirplaneMode(device, enabled = false), "airplane mode must release")
            }

            // 5. The debug egress monitor reads the PERSISTED ledger at
            // wlo://debug/egress: it must land on the monitor route and render
            // the single zoo receipt (screencap evidence + ledger truth).
            deliverDeepLink(scenario, "wlo://debug/egress")
            awaitRoute(scenario, "debug/egress")
            SystemClock.sleep(2_000) // let the ledger query + first frame land
            OnboardingRobot.shell("screencap -p /sdcard/m4-egress-monitor.png")
            val receipts = runBlocking { ledger.recent(10) }
            assertEquals(1, receipts.size, "the monitor's data source holds exactly one receipt")
            assertEquals(EgressPurpose.ZOO_DOWNLOAD, receipts.single().purpose)
        } finally {
            scenario.onActivity { activity -> activity.finish() }
            SystemClock.sleep(500)
        }
    }

    // --- helpers ----------------------------------------------------------------

    private suspend fun zooReceipts(ledger: EgressLedger) = ledger.countByPurpose()[EgressPurpose.ZOO_DOWNLOAD] ?: 0L

    private fun zooBaseDir(): okio.Path {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.filesDir, "zoo").absolutePath.toPath()
    }

    /** Delivers a wlo:// link through the singleTask re-delivery path (TestNav). */
    private fun deliverDeepLink(
        scenario: ActivityScenario<MainActivity>,
        uri: String,
    ) {
        scenario.onActivity { activity ->
            activity.deliverNewIntentForVerification(
                Intent(activity, MainActivity::class.java).setData(Uri.parse(uri)),
            )
        }
    }

    /** Polls the activity's destination hook until the expected route shows. */
    private fun awaitRoute(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var actual: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> actual = activity.currentDestinationForVerification }
            if (actual == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(expected, actual)
    }

    private fun shell(
        device: UiDevice,
        command: String,
    ): String {
        // UiAutomation.executeShellCommand yields a ParcelFileDescriptor on
        // newer APIs, an InputStream on older — normalize to a stream.
        val raw: Any = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val stream: java.io.InputStream =
            if (raw is android.os.ParcelFileDescriptor) {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(raw)
            } else {
                raw as java.io.InputStream
            }
        return stream.use { input -> input.bufferedReader().readText().trim() }
    }

    private fun awaitAirplaneMode(
        device: UiDevice,
        enabled: Boolean,
    ): Boolean {
        val expected = if (enabled) "enabled" else "disabled"
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (shell(device, "cmd connectivity airplane-mode") == expected) return true
            SystemClock.sleep(250)
        }
        return false
    }

    private companion object {
        /** ADR-007 pins (re-verified 2026-09-12 against the upstream artifact). */
        const val ADR007_SHA256: String = "af237923fd4636f5c6156059e372871a2b63e4a851f377f1b92ae8c71d018890"
        const val ADR007_SIZE_BYTES: Long = 9_835_830L
    }
}
