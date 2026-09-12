package app.wlo.core.engines

import app.wlo.core.model.Provenance
import app.wlo.core.testing.GoldenFixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Golden-file harness: scenarios in `resources/golden/bmi_scenarios.json` run
 * unchanged in CI; new fixtures land with the engine change that alters them.
 */
class BmiEngineGoldenTest {
    @Serializable
    private data class Scenario(
        val name: String,
        val weightKg: Double,
        val heightCm: Double,
        val expectedBmi: Double,
        val expectedCategory: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val scenarios: List<Scenario> by lazy {
        json.decodeFromString<List<Scenario>>(GoldenFixtures.load("golden/bmi_scenarios.json"))
    }

    @Test
    fun goldenScenariosMatch() {
        assertTrue(scenarios.size >= 5, "fixture file should carry a real scenario set")
        scenarios.forEach { scenario ->
            val bmi = BmiEngine.bmi(scenario.weightKg, scenario.heightCm)
            assertTrue(
                abs(bmi - scenario.expectedBmi) < 1e-9,
                "${scenario.name}: expected ${scenario.expectedBmi} got $bmi",
            )
            assertEquals(
                scenario.expectedCategory,
                BmiEngine.categorize(bmi).name,
                "${scenario.name}: category mismatch",
            )
        }
    }

    @Test
    fun derivedOutputCarriesRegistryProvenance() {
        val derived = BmiEngine.derived(weightKg = 70.0, heightCm = 175.0)
        val provenance = assertIs<Provenance.Derived>(derived.provenance)
        assertEquals(BmiEngine.FORMULA_VERSION, provenance.formulaVersion)
        assertEquals(listOf("weightKg", "heightCm"), provenance.inputs)
    }

    @Test
    fun nonPositiveInputsAreContractViolations() {
        assertFailsWith<IllegalArgumentException> { BmiEngine.bmi(0.0, 175.0) }
        assertFailsWith<IllegalArgumentException> { BmiEngine.bmi(70.0, -1.0) }
    }
}
