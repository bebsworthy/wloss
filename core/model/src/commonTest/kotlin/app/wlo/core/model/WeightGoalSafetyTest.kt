package app.wlo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WeightGoalSafetyTest {
    @Test
    fun noGoal_isTrackingOnly_withoutInventingADate() {
        val result =
            WeightGoalSafety.evaluate(
                safeInput(ageYears = 15, mode = null, targetWeightKg = null, requestedPace = null)
                    .copy(pregnant = SafetyAnswer.NOT_ANSWERED),
            )

        assertIs<WeightGoalEligibility.TrackingOnly>(result)
        assertFalse(result.allowsGoalMath)
    }

    @Test
    fun adultLossMaintenanceAndGain_areFirstClassModes() {
        val loss = WeightGoalSafety.evaluate(safeInput(mode = WeightGoalMode.LOSS, targetWeightKg = 75.0, requestedPace = 0.5))
        val maintenance =
            WeightGoalSafety.evaluate(safeInput(mode = WeightGoalMode.MAINTENANCE, targetWeightKg = 80.4, requestedPace = null))
        val gain = WeightGoalSafety.evaluate(safeInput(mode = WeightGoalMode.GAIN, targetWeightKg = 85.0, requestedPace = 0.25))

        assertEquals(WeightGoalMode.LOSS, assertIs<WeightGoalEligibility.Eligible>(loss).mode)
        assertEquals(WeightGoalMode.MAINTENANCE, assertIs<WeightGoalEligibility.Eligible>(maintenance).mode)
        assertEquals(WeightGoalMode.GAIN, assertIs<WeightGoalEligibility.Eligible>(gain).mode)
    }

    @Test
    fun minor_isUnsupported_butCopyKeepsTrackingAvailable() {
        val result = WeightGoalSafety.evaluate(safeInput(ageYears = 17))
        val unsupported = assertIs<WeightGoalEligibility.Unsupported>(result)

        assertEquals(WeightGoalUnsupportedReason.MINOR, unsupported.reason)
        assertTrue(WeightGoalSafetyCopyPolicy.forResult(result).body.contains("tracking remains available", ignoreCase = true))
    }

    @Test
    fun unansweredScreening_holdsRatherThanAssumingNo() {
        val result =
            WeightGoalSafety.evaluate(
                safeInput().copy(pregnant = SafetyAnswer.NOT_ANSWERED),
            )

        assertTrue(assertIs<WeightGoalEligibility.Held>(result).reasons.contains(WeightGoalHoldReason.SCREENING_INCOMPLETE))
    }

    @Test
    fun pregnancyBreastfeedingEatingConcernAndMedicalInfluence_eachHoldGoalMath() {
        val cases =
            listOf(
                safeInput().copy(pregnant = SafetyAnswer.YES) to WeightGoalHoldReason.PREGNANCY,
                safeInput().copy(breastfeeding = SafetyAnswer.YES) to WeightGoalHoldReason.BREASTFEEDING,
                safeInput().copy(eatingDisorderConcern = SafetyAnswer.YES) to WeightGoalHoldReason.EATING_DISORDER_CONCERN,
                safeInput().copy(medicallyInfluencedWeight = SafetyAnswer.YES) to WeightGoalHoldReason.MEDICALLY_INFLUENCED_WEIGHT,
            )

        cases.forEach { (input, reason) ->
            val result = assertIs<WeightGoalEligibility.Held>(WeightGoalSafety.evaluate(input))
            assertTrue(reason in result.reasons)
            assertFalse(result.allowsGoalMath)
        }
    }

    @Test
    fun outOfRangePaces_areHeld_notClamped() {
        val loss = assertIs<WeightGoalEligibility.Held>(WeightGoalSafety.evaluate(safeInput(requestedPace = 1.1)))
        val gain =
            assertIs<WeightGoalEligibility.Held>(
                WeightGoalSafety.evaluate(safeInput(mode = WeightGoalMode.GAIN, targetWeightKg = 85.0, requestedPace = 0.6)),
            )

        assertTrue(WeightGoalHoldReason.PACE_OUTSIDE_SUPPORTED_RANGE in loss.reasons)
        assertEquals(1.0, loss.maximumPacePctPerWeek)
        assertTrue(WeightGoalHoldReason.PACE_OUTSIDE_SUPPORTED_RANGE in gain.reasons)
        assertEquals(0.5, gain.maximumPacePctPerWeek)
    }

    @Test
    fun lossCap_usesBothPercentAndGradualAbsoluteEnvelope() {
        assertEquals(1.0, WeightGoalSafety.maximumPacePctPerWeek(WeightGoalMode.LOSS, currentWeightKg = 80.0))
        assertEquals(
            0.45,
            WeightGoalSafety.maximumPacePctPerWeek(WeightGoalMode.LOSS, currentWeightKg = 200.0),
            absoluteTolerance = 1e-9,
        )
    }

    @Test
    fun budgetBelowConfiguredFloor_isHeld() {
        val result =
            WeightGoalSafety.evaluate(
                safeInput().copy(plannedDailyEnergyKcal = 1_199.0, minimumDailyEnergyKcal = 1_200.0),
            )

        assertTrue(
            WeightGoalHoldReason.BELOW_CONFIGURED_ENERGY_FLOOR in assertIs<WeightGoalEligibility.Held>(result).reasons,
        )
    }

    @Test
    fun directionMismatchAndInvalidNumbers_neverProduceGoalMath() {
        val mismatch =
            WeightGoalSafety.evaluate(safeInput(mode = WeightGoalMode.LOSS, targetWeightKg = 85.0, requestedPace = 0.5))
        val invalid = WeightGoalSafety.evaluate(safeInput().copy(currentWeightKg = Double.NaN))

        assertTrue(WeightGoalHoldReason.MODE_TARGET_MISMATCH in assertIs<WeightGoalEligibility.Held>(mismatch).reasons)
        assertEquals(
            WeightGoalUnsupportedReason.INVALID_CURRENT_WEIGHT,
            assertIs<WeightGoalEligibility.Unsupported>(invalid).reason,
        )
    }

    private fun safeInput(
        ageYears: Int? = 35,
        mode: WeightGoalMode? = WeightGoalMode.LOSS,
        targetWeightKg: Double? = 75.0,
        requestedPace: Double? = 0.5,
    ): WeightGoalSafetyInput =
        WeightGoalSafetyInput(
            ageYears = ageYears,
            mode = mode,
            currentWeightKg = 80.0,
            targetWeightKg = targetWeightKg,
            requestedPacePctPerWeek = requestedPace,
            plannedDailyEnergyKcal = 1_800.0,
            minimumDailyEnergyKcal = 1_200.0,
            pregnant = SafetyAnswer.NO,
            breastfeeding = SafetyAnswer.NO,
            eatingDisorderConcern = SafetyAnswer.NO,
            medicallyInfluencedWeight = SafetyAnswer.NO,
        )
}
