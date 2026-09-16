package app.wlo.app.navigation

import androidx.compose.ui.unit.dp
import app.wlo.feature.f06.weight.F06Routes
import app.wlo.feature.f13.vault.F13Routes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WloNavigationPolicyTest {
    @Test
    fun onlyTheFourFunctioningDestinationsAreTopLevel() {
        assertEquals(listOf("Weight", "Hub", "Plan", "More"), WLO_TABS.map { it.label })
        WLO_TABS.forEach { tab -> assertTrue(isTopLevelRoute(tab.route), tab.route) }
        assertTrue(isTopLevelRoute("f06/weight"))
        assertFalse(isTopLevelRoute(WloTabs.ARCHIVE))
        assertFalse(isTopLevelRoute(F13Routes.BACKUP))
        assertFalse(isTopLevelRoute(null))
    }

    @Test
    fun nestedRoutesHaveStableAppBarTitles() {
        assertEquals("Weight", routeTitle("f06/log"))
        assertEquals("Backup", routeTitle(F13Routes.BACKUP))
        assertEquals("Capture food", routeTitle("f02/capture"))
    }

    @Test
    fun settingsAndDietRoutesHaveClearHomes() {
        assertEquals("Health Connect", routeTitle("app/health-connect"))
        assertEquals("App lock", routeTitle("app/settings/lock"))
        assertEquals("Data & backup", routeTitle(F13Routes.VAULT))
        assertEquals(WloTabs.PLAN, routeMetadata(app.wlo.feature.f01.onboarding.F01Routes.PLAN_STUDIO)?.topLevelOwner)
    }

    @Test
    fun navigationAdaptsAtTheMaterialMediumWidthBoundary() {
        assertFalse(useNavigationRail(599.dp, showTopLevelNavigation = true))
        assertTrue(useNavigationRail(600.dp, showTopLevelNavigation = true))
        assertFalse(useNavigationRail(840.dp, showTopLevelNavigation = false))
        assertFalse(useSupportingPane(839.dp))
        assertTrue(useSupportingPane(840.dp))
    }

    @Test
    fun metadataPinsOwnersTitlesAndUpBehavior() {
        assertEquals("Weight", routeMetadata(WloTabs.WEIGHT)?.appBarTitle)
        assertFalse(routeMetadata(WloTabs.WEIGHT)?.showsUp ?: true)
        assertEquals(WloTabs.WEIGHT, routeMetadata(F06Routes.LOGBOOK)?.topLevelOwner)
        assertTrue(routeMetadata(F06Routes.LOGBOOK)?.showsUp == true)
    }
}
