package app.wlo.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The `wlo://` registry is a pure function — these pin its contract. */
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
    public fun registryAliasesMapToOwningTab() {
        assertEquals("hub", WloDeepLinks.routeFor("wlo://weight/log"))
        assertEquals("hub", WloDeepLinks.routeFor("wlo://checkin"))
        assertEquals("hub", WloDeepLinks.routeFor("wlo://log/capture"))
        assertEquals("plan", WloDeepLinks.routeFor("wlo://plan/tomorrow"))
        assertEquals("insights", WloDeepLinks.routeFor("wlo://insights/report"))
        assertEquals("archive", WloDeepLinks.routeFor("wlo://archive/capture"))
        assertEquals("digestion", WloDeepLinks.routeFor("wlo://gut/log"))
    }

    @Test
    public fun queryStringsAndTrailingSlashesAreIgnored() {
        assertEquals("digestion", WloDeepLinks.routeFor("wlo://gut/log?context=meal:123"))
        assertEquals("hub", WloDeepLinks.routeFor("wlo://hub/"))
    }

    @Test
    public fun unknownOrForeignUrisReturnNull() {
        assertNull(WloDeepLinks.routeFor("wlo://nonsense"))
        assertNull(WloDeepLinks.routeFor("https://hub"))
        assertNull(WloDeepLinks.routeFor("not a uri"))
        assertNull(WloDeepLinks.routeFor("wlo://"))
    }

    @Test
    public fun everyTabHasACanonicalPatternAndItRoundTrips() {
        for (route in WloDeepLinks.TAB_ROUTES) {
            val patterns = WloDeepLinks.patternsByRoute[route].orEmpty()
            assertTrue(patterns.contains("wlo://$route"), "tab $route must own wlo://$route")
            assertEquals(route, WloDeepLinks.routeFor(patterns.first()))
        }
    }
}
