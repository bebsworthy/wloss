package app.wlo.feature.f01.onboarding

import app.wlo.core.engines.EngineHoldReason
import app.wlo.core.engines.EngineState
import app.wlo.core.engines.GoalForecastResult
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.Sex
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import app.wlo.feature.f01.onboarding.domain.WeightGoalPreview
import app.wlo.feature.f01.onboarding.domain.WeightGoalPreviewInput
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeightGoalPreviewTest {
    @Test
    fun sameInputProducesSameEligibilityAndForecastForEveryEntryPoint() {
        val firstRun = WeightGoalPreview.evaluate(input())
        val settings = WeightGoalPreview.evaluate(input())

        assertEquals(firstRun, settings)
        assertIs<WeightGoalEligibility.Eligible>(firstRun.eligibility)
        val developing = assertIs<GoalForecastResult.Developing>(firstRun.forecast)
        assertFalse(developing.pointDateEligible)
    }

    @Test
    fun targetDateDrivesImpliedPaceAndRejectsPastDates() {
        val future = WeightGoalPreview.evaluate(input(targetEpochDay = TODAY + 70))
        assertEquals(1.0, assertNotNull(future.impliedPacePctPerWeek), 0.0001)
        assertEquals(1.0, assertNotNull(future.effectivePacePctPerWeek), 0.0001)
        assertTrue(future.targetDateValid)

        val past = WeightGoalPreview.evaluate(input(targetEpochDay = TODAY - 1))
        assertFalse(past.targetDateValid)
        assertNull(past.impliedPacePctPerWeek)
    }

    @Test
    fun heldQualityNeverCalculatesANewDate() {
        val result =
            WeightGoalPreview.evaluate(
                input(engineState = EngineState.Held(EngineHoldReason.WEIGH_GAP, 12)),
            )

        val held = assertIs<GoalForecastResult.Held>(result.forecast)
        assertEquals(EngineHoldReason.WEIGH_GAP, held.reason)
        assertNull(held.lastGoodBands)
    }

    @Test
    fun gainGoalIsSupportedButDateRemainsWithheld() {
        val result =
            WeightGoalPreview.evaluate(
                input(mode = WeightGoalMode.GAIN, targetWeightKg = 90.0),
            )

        assertIs<WeightGoalEligibility.Eligible>(result.eligibility)
        assertIs<GoalForecastResult.Withheld>(result.forecast)
    }

    private fun input(
        mode: WeightGoalMode = WeightGoalMode.LOSS,
        targetWeightKg: Double = 72.0,
        targetEpochDay: Long? = null,
        engineState: EngineState = EngineState.Developing(0),
    ): WeightGoalPreviewInput =
        WeightGoalPreviewInput(
            ageYears = 35,
            sex = Sex.FEMALE,
            heightCm = 170.0,
            activityLevel = ActivityLevel.SEDENTARY,
            currentWeightKg = 80.0,
            targetWeightKg = targetWeightKg,
            mode = mode,
            requestedPacePctPerWeek = 0.5,
            targetEpochDay = targetEpochDay,
            plannedDailyEnergyKcal = 1_800.0,
            pregnant = SafetyAnswer.NO,
            breastfeeding = SafetyAnswer.NO,
            eatingDisorderConcern = SafetyAnswer.NO,
            medicallyInfluencedWeight = SafetyAnswer.NO,
            todayEpochDay = TODAY,
            now = Instant.parse("2026-09-16T12:00:00Z"),
            engineState = engineState,
        )

    private companion object {
        const val TODAY: Long = 20_000
    }
}
