package app.wlo.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecimalInputTest {
    @Test
    fun acceptsCommaOrDotAndRejectsAmbiguousOrNonfiniteInput() {
        assertEquals(80.5, DecimalInput.parse("80,5"))
        assertEquals(80.5, DecimalInput.parse(" 80.5 "))
        assertNull(DecimalInput.parse("80,5.1"))
        assertNull(DecimalInput.parse("NaN"))
        assertNull(DecimalInput.parse("Infinity"))
    }
}
