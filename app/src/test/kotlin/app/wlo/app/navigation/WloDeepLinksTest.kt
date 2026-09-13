package app.wlo.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `wlo://` registry is a pure function — these pin its contract: every
 * IA.md §3 URI resolves to a real screen's route or a documented stub, tab
 * roots are owned by themselves, and queries never change the target.
 */
public class WloDeepLinksTest {
    @Test
    public fun tabRootsResolveToTheirRoute() {
        assertEquals("hub", WloDeepLinks.routeFor("wlo://hub"))
        assertEquals("plan", WloDeepLinks.routeFor("wlo://plan"))
        assertEquals("insights", WloDeepLinks.routeFor("wlo://insights"))
        assertEquals("archive", WloDeepLinks.routeFor("wlo://archive"))
        assertEquals("digestion", WloDeepLinks.routeFor("wlo://digestion"))
    }

    @Test
    public fun realScreensResolveToTheirFeatureRoutes() {
        assertEquals("f06/weight", WloDeepLinks.routeFor("wlo://weight"))
        assertEquals("f06/log", WloDeepLinks.routeFor("wlo://weight/log"))
        assertEquals("f02/capture", WloDeepLinks.routeFor("wlo://log/capture"))
        assertEquals("f02/log", WloDeepLinks.routeFor("wlo://log/search"))
        assertEquals("f02/quick-kcal", WloDeepLinks.routeFor("wlo://log/quick-kcal"))
        assertEquals("f02/diary?entry={entry}", WloDeepLinks.routeFor("wlo://diary"))
        assertEquals("ai/models", WloDeepLinks.routeFor("wlo://ai/models"))
        // M5 PART B: the plan→shop pipeline is real (F03 + F04).
        assertEquals("f03/plan/tomorrow", WloDeepLinks.routeFor("wlo://plan/tomorrow"))
        assertEquals("f03/plan/focus?day={day}&slot={slot}", WloDeepLinks.routeFor("wlo://log/planned"))
        assertEquals("f03/studio?proposal={proposal}", WloDeepLinks.routeFor("wlo://studio"))
        assertEquals("f04/list", WloDeepLinks.routeFor("wlo://list"))
        assertEquals("f04/pantry", WloDeepLinks.routeFor("wlo://pantry"))
    }

    @Test
    public fun futureSurfacesResolveToDocumentedStubs() {
        val stubRoutes =
            WloDeepLinks.REGISTRY.filter(DeepLinkEntry::stub).map(DeepLinkEntry::route)
        assertTrue(stubRoutes.isNotEmpty(), "the registry must carry stubs for later-milestone surfaces")
        for (route in stubRoutes) {
            assertTrue(route.startsWith("stub/"), "stub routes are namespaced: $route")
        }
        assertEquals("stub/energy", WloDeepLinks.routeFor("wlo://energy"))
        assertEquals("stub/checkin", WloDeepLinks.routeFor("wlo://checkin"))
        assertEquals("stub/vault", WloDeepLinks.routeFor("wlo://vault"))
        assertEquals("stub/gut", WloDeepLinks.routeFor("wlo://gut/log"))
        assertEquals("stub/algorithms", WloDeepLinks.routeFor("wlo://algorithms"))
        assertEquals("stub/ai-receipts", WloDeepLinks.routeFor("wlo://ai/receipts"))
    }

    @Test
    public fun queryStringsAndTrailingSlashesAreIgnored() {
        assertEquals("stub/gut", WloDeepLinks.routeFor("wlo://gut/log?context=meal:123"))
        assertEquals("hub", WloDeepLinks.routeFor("wlo://hub/"))
        assertEquals("f02/diary?entry={entry}", WloDeepLinks.routeFor("wlo://log/correct?entry=abc123"))
    }

    @Test
    public fun unknownOrForeignUrisReturnNull() {
        assertNull(WloDeepLinks.routeFor("wlo://nonsense"))
        assertNull(WloDeepLinks.routeFor("https://hub"))
        assertNull(WloDeepLinks.routeFor("not a uri"))
        assertNull(WloDeepLinks.routeFor("wlo://"))
    }

    @Test
    public fun everyRegistryRowRoundTrips() {
        for (registryRow in WloDeepLinks.REGISTRY) {
            val uri = registryRow.uriPattern.substringBefore('?')
            assertEquals(registryRow.route, WloDeepLinks.routeFor("$uri?x=1"), "row ${registryRow.uriPattern}")
            assertTrue(
                WloDeepLinks.patternsByRoute[registryRow.route].orEmpty().contains(registryRow.uriPattern),
                "patternsByRoute carries ${registryRow.uriPattern}",
            )
        }
    }

    @Test
    public fun iaRegistryIsFullyCovered() {
        // IA.md §3's literal list (arg'd URIs pattern-matched by path).
        val iaUris =
            listOf(
                "weight",
                "weight/log",
                "energy",
                "log/capture",
                "log/quick-kcal",
                "log/planned",
                "gut/log",
                "exercise/start",
                "plan/tomorrow",
                "checkin",
                "studio",
                "insights/report",
                "insights/streaks",
                "vault",
                "log/correct",
                "archive/compare",
                "archive/capture",
                "algorithms",
                "ai/receipts",
            )
        for (target in iaUris) {
            val resolved = WloDeepLinks.routeFor("wlo://$target")
            assertTrue(resolved != null, "IA.md §3 target must resolve: wlo://$target")
        }
    }
}
