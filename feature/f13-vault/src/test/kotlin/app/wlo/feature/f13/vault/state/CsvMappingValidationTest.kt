package app.wlo.feature.f13.vault.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvMappingValidationTest {
    @Test
    fun requiresExactlyOneDateAndOneMeasurement() {
        assertEquals("Choose exactly one Date column. Dates must use YYYY-MM-DD.", validateCsvMapping(emptyList()))
        assertEquals(
            "Choose at least one measurement column.",
            validateCsvMapping(listOf(row("date", "day"))),
        )
    }

    @Test
    fun rejectsDerivedTrendAndDuplicateDestinations() {
        assertEquals(
            "Trend is derived by WLO and cannot be imported.",
            validateCsvMapping(listOf(row("date", "day"), row("trend", "trend", "kg"))),
        )
        assertEquals(
            "Each destination can be mapped only once.",
            validateCsvMapping(
                listOf(row("date", "day"), row("weight a", "weight", "kg"), row("weight b", "weight", "lb")),
            ),
        )
    }

    @Test
    fun permitsExplicitWeightUnitAndCustomMetric() {
        assertNull(
            validateCsvMapping(
                listOf(
                    row("date", "day"),
                    row("weight", "weight", "lb"),
                    CsvMappingRow("glucose", "custom", "mmol/L", "glucose"),
                ),
            ),
        )
    }

    private fun row(
        column: String,
        kind: String,
        unit: String? = null,
    ): CsvMappingRow = CsvMappingRow(column, kind, unit, null)
}
