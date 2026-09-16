package app.wlo.core.engines

import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalHoldReason
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafety
import app.wlo.core.model.WeightGoalSafetyInput
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForecastEligibilityTest {
    @Test
    fun `eligible gain goal is held because numerical model is loss only`() {
        val result =
            ForecastEngine.coldStart(
                forecastInput(),
                WeightGoalEligibility.Eligible(
                    mode = WeightGoalMode.GAIN,
                    requestedPacePctPerWeek = 0.25,
                    maximumPacePctPerWeek = 0.5,
                ),
            )

        val withheld = assertIs<GoalForecastResult.Withheld>(result)
        val held = assertIs<WeightGoalEligibility.Held>(withheld.eligibility)
        assertTrue(WeightGoalHoldReason.GAIN_FORECAST_UNAVAILABLE in held.reasons)
    }

    @Test
    fun heldEligibility_withholdsBandsAndDates() {
        val held =
            WeightGoalSafety.evaluate(
                safetyInput().copy(pregnant = SafetyAnswer.YES),
            )

        val result = ForecastEngine.coldStart(forecastInput(), held)

        assertIs<GoalForecastResult.Withheld>(result)
    }

    @Test
    fun eligibleAdult_withoutEvidenceGetsDevelopingColdStartForecast() {
        val eligible = WeightGoalSafety.evaluate(safetyInput())

        val result = ForecastEngine.coldStart(forecastInput(), eligible)

        assertIs<WeightGoalEligibility.Eligible>(eligible)
        val developing = assertIs<GoalForecastResult.Developing>(result)
        assertEquals(ForecastMode.COLD_START, developing.bands.mode)
    }

    @Test
    fun releaseBoundary_developingShowsOnlyWideColdStartEstimate() {
        val result =
            ForecastEngine.evaluate(
                coldStartInput = forecastInput(),
                eligibility = WeightGoalSafety.evaluate(safetyInput()),
                engineState = EngineState.Developing(usableDays = 9),
                measuredInput = measuredInput(),
            )

        val developing = assertIs<GoalForecastResult.Developing>(result)
        assertEquals(ForecastMode.COLD_START, developing.bands.mode)
        assertEquals(9, developing.usableDays)
        assertEquals(10, developing.requiredUsableDays)
        assertFalse(developing.pointDateEligible)
    }

    @Test
    fun releaseBoundary_heldNeverCalculatesANewDate() {
        val result =
            ForecastEngine.evaluate(
                coldStartInput = forecastInput(),
                eligibility = WeightGoalSafety.evaluate(safetyInput()),
                engineState = EngineState.Held(EngineHoldReason.WEIGH_GAP, magnitude = 5),
                measuredInput = measuredInput(),
            )

        val held = assertIs<GoalForecastResult.Held>(result)
        assertEquals(EngineHoldReason.WEIGH_GAP, held.reason)
        assertEquals(5, held.detailDays)
        assertNull(held.lastGoodBands)
    }

    @Test
    fun releaseBoundary_updatingWithMeasuredSolveIsAvailable() {
        val result =
            ForecastEngine.evaluate(
                coldStartInput = forecastInput(),
                eligibility = WeightGoalSafety.evaluate(safetyInput()),
                engineState = EngineState.Updating(usableDays = 10),
                measuredInput = measuredInput(),
            )

        val available = assertIs<GoalForecastResult.Available>(result)
        assertEquals(ForecastMode.MEASURED, available.bands.mode)
    }

    @Test
    fun releaseBoundary_safetyHoldWinsBeforeUpdatingEvidence() {
        val result =
            ForecastEngine.evaluate(
                coldStartInput = forecastInput(),
                eligibility = WeightGoalSafety.evaluate(safetyInput().copy(pregnant = SafetyAnswer.YES)),
                engineState = EngineState.Updating(usableDays = 21),
                measuredInput = measuredInput(),
            )

        assertIs<GoalForecastResult.Withheld>(result)
    }

    @Test
    fun plateauCopyMayClaimRiseOnlyWhenQualityIntervalsDoNotOverlap() {
        val quality = EngineState.Updating(usableDays = 14)

        assertIs<PlateauInterpretation.ApproximateBalance>(
            ForecastEngine.interpretPlateau(
                flatTrend = true,
                previous = TdeeEstimateInterval(2_300.0, 100.0),
                current = TdeeEstimateInterval(2_380.0, 100.0),
                previousState = quality,
                currentState = quality,
            ),
        )
        val increase =
            assertIs<PlateauInterpretation.SupportedIncrease>(
                ForecastEngine.interpretPlateau(
                    flatTrend = true,
                    previous = TdeeEstimateInterval(2_300.0, 50.0),
                    current = TdeeEstimateInterval(2_450.0, 50.0),
                    previousState = quality,
                    currentState = quality,
                ),
            )
        assertEquals(150.0, increase.deltaKcal)
    }

    @Test
    fun plateauClaimIsHeldWhenEitherWindowIsNotQualityPassing() {
        val interpretation =
            ForecastEngine.interpretPlateau(
                flatTrend = true,
                previous = TdeeEstimateInterval(2_300.0, 50.0),
                current = TdeeEstimateInterval(2_500.0, 50.0),
                previousState = EngineState.Updating(usableDays = 14),
                currentState = EngineState.Held(EngineHoldReason.OUTLIER, magnitude = null),
            )

        assertIs<PlateauInterpretation.InsufficientEvidence>(interpretation)
    }

    private fun safetyInput(): WeightGoalSafetyInput =
        WeightGoalSafetyInput(
            ageYears = 35,
            mode = WeightGoalMode.LOSS,
            currentWeightKg = 80.0,
            targetWeightKg = 75.0,
            requestedPacePctPerWeek = 0.5,
            plannedDailyEnergyKcal = 1_800.0,
            minimumDailyEnergyKcal = 1_200.0,
            pregnant = SafetyAnswer.NO,
            breastfeeding = SafetyAnswer.NO,
            eatingDisorderConcern = SafetyAnswer.NO,
            medicallyInfluencedWeight = SafetyAnswer.NO,
        )

    private fun forecastInput(): ColdStartInput =
        ColdStartInput(
            sex = null,
            ageYears = 35,
            heightCm = 172.0,
            startTrendKg = 80.0,
            goalWeightKg = 75.0,
            activityLevel = ActivityLevel.SEDENTARY,
            intakeKcal = 1_800.0,
            startEpochDay = 20_000,
            startInstant = Instant.fromEpochMilliseconds(0),
        )

    private fun measuredInput(): MeasuredInput =
        MeasuredInput(
            sex = null,
            ageYears = 35,
            heightCm = 172.0,
            startTrendKg = 80.0,
            goalWeightKg = 75.0,
            intakeKcal = 1_800.0,
            measuredTdeeKcal = 2_400.0,
            fastPaceKgPerWeek = 0.65,
            slowPaceKgPerWeek = 0.35,
            startEpochDay = 20_000,
            startInstant = Instant.fromEpochMilliseconds(0),
        )
}
