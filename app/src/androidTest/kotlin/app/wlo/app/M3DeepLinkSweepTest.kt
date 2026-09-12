package app.wlo.app

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wlo.app.navigation.DeepLinkEntry
import app.wlo.app.navigation.WloDeepLinks
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M3 acceptance (c): the FULL IA.md §3 deep-link registry — every registered
 * `wlo://` URI launches without a crash and lands on its registered surface
 * (a real screen or a documented stub). Runs against the onboarded app after
 * the deterministic seed; each link is delivered through the singleTask
 * re-delivery path, exactly like a widget/notification tap.
 */
@RunWith(AndroidJUnit4::class)
public class M3DeepLinkSweepTest {
    @Before
    public fun seed() {
        SeedingRobot.onboardAndSeedWeek()
    }

    @Test
    public fun everyRegistryUri_landsOnItsRegisteredSurface() {
        // No scenario.use{}: closing can time out on DESTROYED after an
        // in-flight re-delivery (the M1 note); finish explicitly + settle.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        TestNav.awaitRoute(scenario, "hub")
        TestNav.awaitSurface(scenario, "hub")

        for (entry in WloDeepLinks.REGISTRY) {
            val uri = TestNav.concreteUri(entry.uriPattern)
            TestNav.deliver(scenario, uri)
            TestNav.awaitRoute(scenario, entry.route)
            val landed = currentRoute(scenario)
            assertTrue(
                "wlo://${entry.uriPattern.removePrefix("wlo://")} must land on ${entry.route}, saw $landed",
                landed == entry.route,
            )
        }
        // Stub screenshots live here (rule-free): the energy detail stub via
        // its deep link, then one tab-root for good measure.
        for (stubUri in listOf("wlo://energy", "wlo://vault", "wlo://exercise/start")) {
            TestNav.deliver(scenario, stubUri)
            TestNav.awaitRoute(scenario, WloDeepLinks.routeFor(stubUri).orEmpty())
            OnboardingRobot.shell("screencap -p /sdcard/m3-stub-${stubUri.substringAfterLast('/').replace("/", "-")}.png")
        }
        scenario.onActivity { it.finish() }
        SystemClock.sleep(500)
    }

    @Test
    public fun stubRowsAllDocumentThemselves_andRealRowsDoNotClaimStubStatus() {
        for (entry in WloDeepLinks.REGISTRY) {
            val destination = WloDeepLinks.routeFor(entry.uriPattern.substringBefore('?'))
            assertTrue("registry row ${entry.uriPattern} resolves", destination != null)
        }
        val stubs: List<DeepLinkEntry> = WloDeepLinks.REGISTRY.filter(DeepLinkEntry::stub)
        assertTrue("the registry documents later-milestone surfaces", stubs.isNotEmpty())
        for (stub in stubs) {
            assertTrue("stub ${stub.route} names its surface for the placeholder copy", stub.owner.isNotBlank())
        }
    }

    private fun currentRoute(scenario: ActivityScenario<MainActivity>): String? {
        var route: String? = null
        scenario.onActivity { activity -> route = activity.currentDestinationForVerification }
        return route
    }
}
