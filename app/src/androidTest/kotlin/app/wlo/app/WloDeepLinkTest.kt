package app.wlo.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `wlo://` deep-link acceptance (IA.md §3), including the F01 cold-start rule:
 * a fresh install lands `wlo://hub` on the WIZARD (the hub route hosts it
 * until a plan exists), and once onboarded the same link lands on the Hub.
 */
@RunWith(AndroidJUnit4::class)
public class WloDeepLinkTest {
    @Test
    public fun launch_withWloHubIntent_whenFresh_landsOnWizardOnTheHubRoute() {
        val scenario = launchWith("wlo://hub")
        scenario.use { freshScenario ->
            awaitRoute(freshScenario, "hub")
            // Content, not just route: the wizard owns the surface until a plan exists.
            awaitSurface(freshScenario, "onboarding")
        }
    }

    @Test
    public fun launch_withWloHubIntent_afterOnboarding_landsOnHub() {
        // Onboard in this session (UiAutomator drive — no compose rule here),
        // then re-deliver the hub link: the Hub itself must take over.
        // No scenario.use{} here: ActivityScenario.close() can time out
        // waiting for DESTROYED after an in-flight re-delivery; the M1
        // workaround (finish explicitly, then sleep) applies.
        val fresh =
            ActivityScenario.launch<MainActivity>(
                Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
            )
        awaitRoute(fresh, "f06/weight")
        OnboardingRobot.driveThroughWizardWithUiAutomator()
        awaitSurface(fresh, "weight")

        fresh.onActivity { activity ->
            activity.deliverNewIntentForVerification(
                Intent(activity, MainActivity::class.java).setData(Uri.parse("wlo://hub")),
            )
        }
        awaitRoute(fresh, "hub")
        awaitSurface(fresh, "hub")
        fresh.onActivity { activity -> activity.finish() }
        SystemClock.sleep(500)
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
        awaitRoute(scenario, "f06/weight")
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

    /** Poll WHAT the hub route renders ("onboarding" while fresh, "hub" after). */
    private fun awaitSurface(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var actual: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> actual = activity.currentSurfaceForVerification }
            if (actual == expected) return
            SystemClock.sleep(100)
        }
        assertTrue("expected surface \"$expected\", saw \"$actual\"", actual == expected)
    }
}
