package app.wlo.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class UnitsTest {
    @Test
    fun metricIsDefault() {
        assertEquals(MassUnit.KILOGRAM, MassUnit.DEFAULT)
        assertEquals(LengthUnit.CENTIMETER, LengthUnit.DEFAULT)
    }

    @Test
    fun massFormattingRoundsToTenths() {
        assertEquals("81.2 kg", MassUnit.KILOGRAM.format(81.24))
        assertEquals("81.3 kg", MassUnit.KILOGRAM.format(81.25))
    }

    @Test
    fun poundConversionRoundTrips() {
        val lb = MassUnit.POUND
        assertEquals(179.9, lb.format(lb.toKilograms(lb.fromKilograms(81.6))).removeSuffix(" lb").toDouble())
        assertEquals(81.6, lb.toKilograms(lb.fromKilograms(81.6)), absoluteTolerance = 1e-9)
    }
}
