package app.wlo.app

import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.vault.LockTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertTrue

/**
 * M6 PART B acceptance (e): the app-lock gate. Enable + IMMEDIATE timeout,
 * then a background blip (HOME) and resume must show the gate. On the
 * credential-LESS emulator the gate must DEGRADE HONESTLY (F13 §3
 * convenience-lock posture): the no-screen-lock state with the one-tap way
 * out — turning the lock off from the gate itself restores the shell.
 */
@RunWith(AndroidJUnit4::class)
public class M6AppLockGateTest {
    @get:Rule
    public val rule = createAndroidComposeRule<MainActivity>()

    private fun koin() = GlobalContext.get()

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            // While the activity is STOPPED (the background blip) the compose
            // hierarchy is unreachable — treat that as "not yet", not a crash.
            if (runCatching(condition).getOrDefault(false)) return
            // The compose rule parks the app's frame clock between test
            // actions; pump it so pending recompositions can run.
            runCatching {
                rule.mainClock.autoAdvance = false
                rule.mainClock.advanceTimeBy(200)
                rule.mainClock.autoAdvance = true
            }
            Thread.sleep(100)
        }
        error("condition never became true")
    }

    @Test
    public fun immediateTimeout_showsGate_afterBackgroundBlip() {
        val settings = koin().get<SettingsStore>()
        val controller = koin().get<app.wlo.core.vault.AppLockController>()
        runBlocking {
            settings.setAppLockEnabled(true)
            settings.setLockTimeout(LockTimeout.IMMEDIATE.wireName)
        }

        val ui = OnboardingRobot.device()

        // Resume the app: ON_START with IMMEDIATE → locked. The home blip is
        // retried: the headless emulator occasionally delivers the relaunch
        // before the stop is recorded, which yields no gate on that attempt.
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val gateVisible = {
            runCatching {
                rule.onAllNodesWithTag("applock-gate", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        for (attempt in 1..3) {
            ui.pressHome()
            Thread.sleep(800)
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && !gateVisible()) Thread.sleep(250)
            if (gateVisible()) break
        }
        waitUntil { gateVisible() }
        rule.waitForIdle()
        TestNav.awaitTag(rule, "applock-no-credential")
        assertTrue(controller.locked.value, "the controller must be in the locked state")

        // The honest escape hatch: no credential anywhere → turn it off.
        rule
            .onAllNodesWithTag("applock-turn-off", useUnmergedTree = true)
            .onFirst()
            .performClick()
        waitUntil {
            rule.onAllNodesWithTag("applock-gate", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        runBlocking {
            val enabled = settings.appLockEnabled.first()
            assertTrue(!enabled, "the gate's turn-off must persist")
        }

        // And once unlocked, the shell is back — land on the studio to prove
        // the whole graph still runs post-unlock. In-process delivery: an
        // `am start` under the compose rule leaves the activity paused (no
        // frames, no recomposition), while the M3-sweep in-process path +
        // the clock pump deliver deterministically.
        TestNav.deliverPumpingClock(rule, "wlo://ai/studio", "f12/studio")
    }

    @Test
    public fun lockSettingsSurface_updatesTimeout_andShowsHonestAvailability() {
        // The Settings screen: app-lock section with the honest no-lock state
        // (the emulator has no screen lock) and the timeout chips. The
        // in-process re-delivery + clock pump (an `am start` under the rule
        // parks the activity — see TestNav.deliverPumpingClock).
        TestNav.deliverPumpingClock(rule, "wlo://settings", "app/settings")
        TestNav.awaitTag(rule, "settings-applock")
        TestNav.awaitTag(rule, "settings-applock-no-lock")
        rule.waitForIdle()

        val settings = koin().get<SettingsStore>()
        runBlocking { settings.setAppLockEnabled(true) }
        waitUntil {
            rule.onAllNodesWithTag("settings-lock-timeout-5min", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("settings-lock-timeout-5min", useUnmergedTree = true).performClick()
        waitUntil {
            runBlocking { LockTimeout.fromWire(settings.lockTimeout.first()) == LockTimeout.FIVE_MINUTES }
        }
    }
}
