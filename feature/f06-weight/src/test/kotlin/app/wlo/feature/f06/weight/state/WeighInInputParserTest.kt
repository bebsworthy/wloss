package app.wlo.feature.f06.weight.state

import app.wlo.core.common.MassUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeighInInputParserTest {
    @Test
    fun `trimmed dot and comma decimals parse in kilograms`() {
        assertEquals(78.4, parsed(" 78.4 ", MassUnit.KILOGRAM), 1e-12)
        assertEquals(78.4, parsed("78,4", MassUnit.KILOGRAM), 1e-12)
    }

    @Test
    fun `pounds convert exactly once to canonical kilograms`() {
        val pounds = 170.0
        val kilograms = parsed("170.0", MassUnit.POUND)

        assertEquals(MassUnit.POUND.toKilograms(pounds), kilograms, 1e-12)
        assertEquals(pounds, MassUnit.POUND.fromKilograms(kilograms), 1e-12)
    }

    @Test
    fun `empty malformed NaN and infinite values are rejected`() {
        listOf("", "   ", "hello", "78.4.2", "78,4.2", "NaN", "Infinity", "-Infinity").forEach { text ->
            assertTrue(WeighInInputParser.parse(text, MassUnit.KILOGRAM).isFailure, text)
        }
    }

    @Test
    fun `canonical range is inclusive and checked after unit conversion`() {
        assertEquals(30.0, parsed("30", MassUnit.KILOGRAM), 1e-12)
        assertEquals(300.0, parsed("300", MassUnit.KILOGRAM), 1e-12)
        assertTrue(WeighInInputParser.parse("29.9", MassUnit.KILOGRAM).isFailure)
        assertTrue(WeighInInputParser.parse("300.1", MassUnit.KILOGRAM).isFailure)

        val minimumPounds = MassUnit.POUND.fromKilograms(30.0)
        val maximumPounds = MassUnit.POUND.fromKilograms(300.0)
        assertTrue(WeighInInputParser.parse((minimumPounds - 0.01).toString(), MassUnit.POUND).isFailure)
        assertTrue(WeighInInputParser.parse((maximumPounds + 0.01).toString(), MassUnit.POUND).isFailure)
    }

    private fun parsed(
        text: String,
        unit: MassUnit,
    ): Double = WeighInInputParser.parse(text, unit).getOrThrow().kilograms
}
