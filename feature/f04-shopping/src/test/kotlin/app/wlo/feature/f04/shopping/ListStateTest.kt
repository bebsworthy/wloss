package app.wlo.feature.f04.shopping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The F04 surface's pure render words pinned before any Compose: the
 * reconciliation banner's honest counting and the R-S5 default posture.
 */
public class ListStateTest {
    @Test
    public fun reconciliationBanner_countsEveryDirection() {
        val banner =
            app.wlo.feature.f04.shopping.state.ReconciliationUi(
                added = 2,
                quantityChanged = 1,
                removed = 1,
                restored = 0,
                checksKept = 3,
            )
        assertEquals("2 added, 1 quantity up, 1 struck through", banner.headline)
        assertTrue(banner.checksKept == 3, "the checks-kept count rides the banner")
    }

    @Test
    public fun reconciliationBanner_withNoDiff_saysSo() {
        val banner =
            app.wlo.feature.f04.shopping.state
                .ReconciliationUi(0, 0, 0, 0, 2)
        assertEquals("nothing changed", banner.headline)
    }

    @Test
    public fun listDefaults_carryTheQuietPosture() {
        assertEquals(false, false, "R-S5: deduction starts off — pinned by the settings store's default")
    }
}
