package app.wlo.feature.f10.hub.state

import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.Profile
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.Sex
import app.wlo.core.model.UnitSystem
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalHoldReason
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafetyInput
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HubForecastSafetyTest {
    @Test
    fun missingAttestationHoldsForecastAndAllDates() {
        val result = evaluate(attestation = null)

        val held = assertIs<WeightGoalEligibility.Held>(result)
        assertTrue(WeightGoalHoldReason.SCREENING_INCOMPLETE in held.reasons)
    }

    @Test
    fun heldAttestationStaysHeldWhenHubReevaluatesCurrentInputs() {
        val result = evaluate(attestation = attestation(pregnant = SafetyAnswer.YES))

        val held = assertIs<WeightGoalEligibility.Held>(result)
        assertTrue(WeightGoalHoldReason.PREGNANCY in held.reasons)
    }

    @Test
    fun eligibleAdultLossCanReachMeasuredOrColdStartForecast() {
        assertIs<WeightGoalEligibility.Eligible>(evaluate(attestation = attestation()))
    }

    @Test
    fun changedUnsafePaceCannotReuseAnOldEligibleAttestation() {
        val result = evaluate(attestation = attestation(), pacePctPerWeek = 1.5)

        val held = assertIs<WeightGoalEligibility.Held>(result)
        assertTrue(WeightGoalHoldReason.PACE_OUTSIDE_SUPPORTED_RANGE in held.reasons)
    }

    private fun evaluate(
        attestation: WeightGoalSafetyInput?,
        pacePctPerWeek: Double = 0.5,
    ): WeightGoalEligibility =
        HubForecastSafety.evaluate(
            profile = PROFILE,
            currentWeightKg = 82.0,
            targetWeightKg = 74.0,
            pacePctPerWeek = pacePctPerWeek,
            plannedDailyEnergyKcal = 1_900.0,
            currentYear = 2026,
            attestation = attestation,
        )

    private fun attestation(pregnant: SafetyAnswer = SafetyAnswer.NO): WeightGoalSafetyInput =
        WeightGoalSafetyInput(
            ageYears = 36,
            mode = WeightGoalMode.LOSS,
            currentWeightKg = 82.0,
            targetWeightKg = 74.0,
            requestedPacePctPerWeek = 0.5,
            plannedDailyEnergyKcal = 1_900.0,
            minimumDailyEnergyKcal = 1_200.0,
            pregnant = pregnant,
            breastfeeding = SafetyAnswer.NO,
            eatingDisorderConcern = SafetyAnswer.NO,
            medicallyInfluencedWeight = SafetyAnswer.NO,
        )

    private companion object {
        val PROFILE =
            Profile(
                id = "profile",
                sex = Sex.FEMALE,
                birthYear = 1990,
                heightCm = 170.0,
                startWeightKg = 82.0,
                activityLevel = ActivityLevel.SEDENTARY,
                unitPreference = UnitSystem.METRIC,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
    }
}
