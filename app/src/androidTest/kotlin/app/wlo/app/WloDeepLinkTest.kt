package app.wlo.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `wlo://` deep-link acceptance (IA.md §3): launching the activity with a
 * `wlo://` intent lands on the owning tab; re-delivery (singleTask
 * onNewIntent) switches tabs. Verified through the activity's destination
 * hook — no view-tree scraping.
 */
@RunWith(AndroidJUnit4::class)
public class WloDeepLinkTest {
    @Test
    public fun launch_withWloHubIntent_landsOnHub() {
        val scenario = launchWith("wlo://hub")
        scenario.use { awaitRoute(it, "hub") }
    }

    @Test
    public fun launch_withWloDigestionIntent_landsOnDigestion() {
        val scenario = launchWith("wlo://digestion")
        scenario.use { awaitRoute(it, "digestion") }
    }

    @Test
    public fun newIntent_withDeepLink_switchesTab() {
        // No scenario.use here: ActivityScenario.close() can time out waiting
        // for DESTROYED after an in-flight re-delivery; finish explicitly.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitRoute(scenario, "hub")
        scenario.onActivity { activity ->
            activity.deliverNewIntentForVerification(
                Intent(activity, MainActivity::class.java).setData(Uri.parse("wlo://digestion")),
            )
        }
        awaitRoute(scenario, "digestion")
        scenario.onActivity { activity -> activity.finish() }
        SystemClock.sleep(500)
    }

    private fun launchWith(uri: String): ActivityScenario<MainActivity> =
        ActivityScenario.launch<MainActivity>(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).setData(Uri.parse(uri)),
        )

    /** Poll the activity's destination hook; composition + nav settle asynchronously. */
    private fun awaitRoute(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var actual: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> actual = activity.currentDestinationForVerification }
            if (actual == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(expected, actual)
    }
}
