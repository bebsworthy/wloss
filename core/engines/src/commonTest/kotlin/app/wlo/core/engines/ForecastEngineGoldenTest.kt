package app.wlo.core.engines

import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.Provenance
import app.wlo.core.model.Sex
import app.wlo.core.testing.GoldenFixtures
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Golden-file harness for the R-A5/R-A6 cold-start forecast: loss and gain
 * profiles in `resources/golden/forecast_scenarios.json` run unchanged in CI;
 * every value was hand-verified against the published formulas (Mifflin-St
 * Jeor + activity multiplier + 7,700 kcal/kg deceleration integration) before
 * being frozen — see the scenario names for the worked arithmetic anchors.
 */
class ForecastEngineGoldenTest {
    @Serializable
    private data class Scenario(
        val name: String,
        val sex: String?,
        val ageYears: Int,
        val heightCm: Double,
        val startTrendKg: Double,
        val goalWeightKg: Double,
        val activityLevel: String,
        val intakeKcal: Double,
        val startEpochDay: Long,
        val expectedTdeeEstimateKcal: Double,
        val expectedFirstRateKgPerWeek: Double,
        val optimisticFinishEpochDay: Long?,
        val expectedFinishEpochDay: Long?,
        val pessimisticFinishEpochDay: Long?,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val scenarios: List<Scenario> by lazy {
        json.decodeFromString(GoldenFixtures.load("golden/forecast_scenarios.json"))
    }

    private fun input(s: Scenario): ColdStartInput =
        ColdStartInput(
            sex = s.sex?.let { Sex.entries.first { sex -> sex.wireName == it } },
            ageYears = s.ageYears,
            heightCm = s.heightCm,
            startTrendKg = s.startTrendKg,
            goalWeightKg = s.goalWeightKg,
            activityLevel = ActivityLevel.entries.first { it.wireName == s.activityLevel },
            intakeKcal = s.intakeKcal,
            startEpochDay = s.startEpochDay,
            startInstant = Instant.fromEpochMilliseconds(0),
        )

    @Test
    fun goldenScenariosMatch() {
        assertTrue(scenarios.size >= 6, "fixture file should carry loss and gain profiles")
        scenarios.forEach { scenario ->
            val bands = ForecastEngine.coldStart(input(scenario))
            assertEquals(
                scenario.expectedTdeeEstimateKcal,
                bands.tdeeEstimateKcal,
                "${scenario.name}: TDEE estimate",
            )
            assertEquals(
                scenario.expectedFirstRateKgPerWeek,
                bands.expected.weeklyRatesKg.first(),
                "${scenario.name}: first weekly rate",
            )
            assertEquals(
                scenario.optimisticFinishEpochDay,
                bands.optimistic.finishEpochDay,
                "${scenario.name}: optimistic finish",
            )
            assertEquals(
                scenario.expectedFinishEpochDay,
                bands.expected.finishEpochDay,
                "${scenario.name}: expected finish",
            )
            assertEquals(
                scenario.pessimisticFinishEpochDay,
                bands.pessimistic.finishEpochDay,
                "${scenario.name}: pessimistic finish",
            )
        }
    }

    @Test
    fun coldStartIsEstimatedAndVersioned() {
        val bands = ForecastEngine.coldStart(input(scenarios.first()))
        assertIs<Provenance.Estimated>(bands.provenance)
        assertEquals(ForecastMode.COLD_START, bands.mode)
        assertEquals("transparent-v1", bands.engineVersion)
        assertEquals("forecast/directional-3band-v2", bands.modelVersion)
        assertEquals("bmr/mifflin-st-jeor-v1", bands.bmrVersion)
    }

    @Test
    fun measuredModeCrossesPaceDistribution() {
        val base = scenarios.first()
        val input =
            MeasuredInput(
                sex = Sex.FEMALE,
                ageYears = base.ageYears,
                heightCm = base.heightCm,
                startTrendKg = base.startTrendKg,
                goalWeightKg = base.goalWeightKg,
                intakeKcal = base.intakeKcal,
                measuredTdeeKcal = 2500.0,
                fastPaceKgPerWeek = 0.65,
                slowPaceKgPerWeek = 0.25,
                typicalStepsPerDay = 8_000,
                startEpochDay = base.startEpochDay,
                startInstant = Instant.fromEpochMilliseconds(0),
            )
        val bands = assertNotNull(ForecastEngine.measured(input))
        assertEquals(ForecastMode.MEASURED, bands.mode)
        assertIs<Provenance.Derived>(bands.provenance)
        // Measured TDEE 2500 vs cold-start ~2406: a bigger deficit finishes no later.
        val cold = ForecastEngine.coldStart(input(base))
        assertTrue(
            (bands.expected.finishEpochDay ?: Long.MAX_VALUE) <= (cold.expected.finishEpochDay ?: Long.MAX_VALUE),
            "measured TDEE above the formula estimate must not slow the expected date",
        )
    }

    @Test
    fun measuredModeNeedsAMeasuredTdee() {
        val base = scenarios.first()
        val input =
            MeasuredInput(
                sex = Sex.FEMALE,
                ageYears = base.ageYears,
                heightCm = base.heightCm,
                startTrendKg = base.startTrendKg,
                goalWeightKg = base.goalWeightKg,
                intakeKcal = base.intakeKcal,
                measuredTdeeKcal = null,
                startEpochDay = base.startEpochDay,
                startInstant = Instant.fromEpochMilliseconds(0),
            )
        assertEquals(null, ForecastEngine.measured(input), "R-A5: cold start is the only zero-data mode")
    }
}
