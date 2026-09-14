package app.wlo.core.engines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The girth ratios (F06 §3 / WLO-0043): pure math + provenance stamps. */
class GirthRatiosEngineTest {
    @Test
    fun waistToHeight_dividesInSameUnits() {
        val ratio = GirthRatiosEngine.waistToHeight(waistCm = 88.0, heightCm = 176.0)
        assertEquals(0.5, ratio.value, 1e-9)
        assertEquals("ratios/waist-height-v1", (ratio.provenance as app.wlo.core.model.Provenance.Derived).formulaVersion)
    }

    @Test
    fun waistToHip_dividesWaistByHip() {
        val ratio = GirthRatiosEngine.waistToHip(waistCm = 90.0, hipCm = 100.0)
        assertEquals(0.9, ratio.value, 1e-9)
        assertEquals("ratios/waist-hip-v1", (ratio.provenance as app.wlo.core.model.Provenance.Derived).formulaVersion)
    }

    @Test
    fun refusesNonPositiveInputs() {
        assertFailsWith<IllegalArgumentException> { GirthRatiosEngine.waistToHeight(0.0, 176.0) }
        assertFailsWith<IllegalArgumentException> { GirthRatiosEngine.waistToHip(90.0, -1.0) }
    }
}
