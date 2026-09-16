package app.wlo.core.engines

import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GainForecastTest {
    private val eligible =
        WeightGoalEligibility.Eligible(
            mode = WeightGoalMode.GAIN,
            requestedPacePctPerWeek = 0.25,
            maximumPacePctPerWeek = 0.5,
        )

    @Test
    fun `gated cold-start gain has ordered bands and monotonic trajectories`() {
        val result = assertIs<GoalForecastResult.Developing>(ForecastEngine.coldStart(input(), eligible))
        val bands = result.bands
        val finishes =
            listOf(
                assertNotNull(bands.optimistic.finishEpochDay),
                assertNotNull(bands.expected.finishEpochDay),
                assertNotNull(bands.pessimistic.finishEpochDay),
            )
        assertTrue(finishes.zipWithNext().all { (a, b) -> a <= b })
        listOf(bands.optimistic, bands.expected, bands.pessimistic).forEach { band ->
            assertTrue(band.trajectoryKg.zipWithNext().all { (a, b) -> b >= a })
            assertEquals(85.0, band.trajectoryKg.last())
        }
    }

    @Test
    fun `gain rate decelerates as higher mass raises expenditure`() {
        val rates = ForecastEngine.coldStart(input()).expected.weeklyRatesKg
        assertTrue(kotlin.math.abs(rates.first()) > kotlin.math.abs(rates.last()))
        assertTrue(rates.zipWithNext().all { (a, b) -> b >= a - 1e-12 })
        assertTrue(rates.all { it <= 0.0 && it.isFinite() })
    }

    @Test
    fun `zero surplus holds without inventing a finish date`() {
        val baseline = ForecastEngine.coldStart(input())
        val bands = ForecastEngine.coldStart(input().copy(intakeKcal = baseline.tdeeEstimateKcal))
        assertNull(bands.expected.finishEpochDay)
        assertTrue(bands.expected.weeklyRatesKg.all { it == 0.0 })
        assertTrue(bands.expected.trajectoryKg.all { it == 80.0 })
    }

    @Test
    fun `target beyond surplus equilibrium terminates at the finite horizon`() {
        val baseline = ForecastEngine.coldStart(input())
        val bands =
            ForecastEngine.coldStart(
                input().copy(
                    goalWeightKg = 100.0,
                    intakeKcal = baseline.tdeeEstimateKcal + 110.0,
                ),
            )
        assertNull(bands.expected.finishEpochDay)
        assertEquals(260, bands.expected.weeklyRatesKg.size)
        assertTrue(bands.expected.trajectoryKg.last() < 85.0)
    }

    @Test
    fun `already-met target finishes immediately`() {
        val start = input().startEpochDay
        val bands = ForecastEngine.coldStart(input().copy(goalWeightKg = 80.0))
        assertEquals(start, bands.expected.finishEpochDay)
        assertTrue(bands.expected.trajectoryKg.isEmpty())
    }

    @Test
    fun `measured gain is available only through eligible release boundary`() {
        val result =
            ForecastEngine.evaluate(
                coldStartInput = input(),
                eligibility = eligible,
                engineState = EngineState.Updating(usableDays = 14),
                measuredInput =
                    MeasuredInput(
                        sex = null,
                        ageYears = 35,
                        heightCm = 172.0,
                        startTrendKg = 80.0,
                        goalWeightKg = 85.0,
                        intakeKcal = 2_600.0,
                        measuredTdeeKcal = 2_300.0,
                        startEpochDay = 20_000,
                        startInstant = Instant.fromEpochMilliseconds(0),
                    ),
            )
        val available = assertIs<GoalForecastResult.Available>(result)
        assertNotNull(available.bands.expected.finishEpochDay)
    }

    private fun input(): ColdStartInput =
        ColdStartInput(
            sex = null,
            ageYears = 35,
            heightCm = 172.0,
            startTrendKg = 80.0,
            goalWeightKg = 85.0,
            activityLevel = ActivityLevel.SEDENTARY,
            intakeKcal = 2_500.0,
            startEpochDay = 20_000,
            startInstant = Instant.fromEpochMilliseconds(0),
        )
}
