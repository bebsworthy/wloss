package app.wlo.feature.f01.onboarding

import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.Sex
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalHoldReason
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalUnsupportedReason
import app.wlo.feature.f01.onboarding.domain.GoalEditorSafety
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GoalEditorSafetyTest {
    @Test
    fun unansweredScreeningHoldsEditorGoalMath() {
        val result = evaluate()

        val held = assertIs<WeightGoalEligibility.Held>(result)
        assertTrue(WeightGoalHoldReason.SCREENING_INCOMPLETE in held.reasons)
    }

    @Test
    fun pregnancyHoldsEditorGoalMathAfterScreening() {
        val result = evaluate(pregnant = SafetyAnswer.YES, otherAnswers = SafetyAnswer.NO)

        val held = assertIs<WeightGoalEligibility.Held>(result)
        assertTrue(WeightGoalHoldReason.PREGNANCY in held.reasons)
    }

    @Test
    fun minorIsUnsupportedAtTheSameEditorBoundary() {
        val result = evaluate(ageYears = 17, pregnant = SafetyAnswer.NO, otherAnswers = SafetyAnswer.NO)

        assertEquals(
            WeightGoalUnsupportedReason.MINOR,
            assertIs<WeightGoalEligibility.Unsupported>(result).reason,
        )
    }

    @Test
    fun supportedAdultLossIsEligible() {
        assertIs<WeightGoalEligibility.Eligible>(
            evaluate(pregnant = SafetyAnswer.NO, otherAnswers = SafetyAnswer.NO),
        )
    }

    @Test
    fun energyBelowConfiguredFloorIsHeld() {
        val result =
            evaluate(
                plannedDailyEnergyKcal = 1_100.0,
                pregnant = SafetyAnswer.NO,
                otherAnswers = SafetyAnswer.NO,
            )

        val held = assertIs<WeightGoalEligibility.Held>(result)
        assertTrue(WeightGoalHoldReason.BELOW_CONFIGURED_ENERGY_FLOOR in held.reasons)
    }

    private fun evaluate(
        ageYears: Int = 35,
        plannedDailyEnergyKcal: Double = 1_900.0,
        pregnant: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
        otherAnswers: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    ): WeightGoalEligibility =
        GoalEditorSafety.evaluate(
            ageYears = ageYears,
            currentWeightKg = 82.0,
            sex = Sex.FEMALE,
            mode = WeightGoalMode.LOSS,
            targetWeightKg = 74.0,
            pacePctPerWeek = 0.5,
            plannedDailyEnergyKcal = plannedDailyEnergyKcal,
            pregnant = pregnant,
            breastfeeding = otherAnswers,
            eatingDisorderConcern = otherAnswers,
            medicallyInfluencedWeight = otherAnswers,
        )
}
