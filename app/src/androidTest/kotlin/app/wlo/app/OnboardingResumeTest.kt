package app.wlo.app

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Draft-resume acceptance (F01 §1: killing the wizard mid-flow resumes, never
 * corrupts). No compose rule here — the test owns the full activity lifecycle:
 * advance two steps, close the activity (the draft is disk-backed via an
 * atomic DataStore write; the relaunch has NO in-memory wizard state), then
 * relaunch and expect to stand exactly where the user stood.
 */
@RunWith(AndroidJUnit4::class)
public class OnboardingResumeTest {
    @Test
    public fun closeMidFlow_relaunch_resumesAtThePersistedStepWithoutCorruption() {
        val ui: UiDevice = OnboardingRobot.device()
        val first = ActivityScenario.launch(MainActivity::class.java)

        clickWhenPresent(ui, "Set up my plan")
        clickWhenPresent(ui, "Continue")
        // On the forecast step: the honest hedge is on screen.
        assertTrue("the forecast step never appeared", waitForKeyword(ui, FORECAST_MARKER))

        // Give the draft write a beat, then close the app mid-flow.
        Thread.sleep(1_500)
        first.close()
        SystemClock.sleep(500)

        ActivityScenario.launch(MainActivity::class.java)
        assertTrue(
            "the relaunch did not resume the draft at the forecast step",
            waitForKeyword(ui, FORECAST_MARKER),
        )
        // And it did NOT restart from the top.
        val restarted = ui.findObjects(By.textContains("Set up my plan")).isNotEmpty()
        assertTrue("the wizard restarted from step one — draft was lost", !restarted)
    }

    private fun clickWhenPresent(
        ui: UiDevice,
        text: String,
    ) {
        // NOTE: no waitForIdle here — it can block for its full timeout while
        // Compose keeps the accessibility stream busy; poll the tree instead.
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val nodes = ui.findObjects(By.textContains(text))
            if (nodes.isNotEmpty()) {
                nodes.last().click()
                return
            }
            SystemClock.sleep(250)
        }
        error("button containing \"$text\" never appeared")
    }

    private fun waitForKeyword(
        ui: UiDevice,
        text: String,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (ui.findObjects(By.textContains(text)).isNotEmpty()) return true
            SystemClock.sleep(250)
        }
        return false
    }

    private companion object {
        const val FORECAST_MARKER: String = "an estimate, not a promise"
    }
}
