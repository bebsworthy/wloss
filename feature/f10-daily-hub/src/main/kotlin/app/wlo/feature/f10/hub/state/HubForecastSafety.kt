package app.wlo.feature.f10.hub.state

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.Profile
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalHoldReason
import app.wlo.core.model.WeightGoalSafety
import app.wlo.core.model.WeightGoalSafetyInput

/** Re-evaluates a saved attestation against the Hub's current forecast inputs. */
internal object HubForecastSafety {
    fun evaluate(
        profile: Profile,
        currentWeightKg: Double,
        targetWeightKg: Double,
        pacePctPerWeek: Double,
        plannedDailyEnergyKcal: Double,
        currentYear: Int,
        attestation: WeightGoalSafetyInput?,
    ): WeightGoalEligibility {
        if (attestation == null) {
            return WeightGoalEligibility.Held(
                mode = null,
                reasons = setOf(WeightGoalHoldReason.SCREENING_INCOMPLETE),
            )
        }
        return WeightGoalSafety.evaluate(
            attestation.copy(
                ageYears = profile.ageAtYear(currentYear),
                currentWeightKg = currentWeightKg,
                targetWeightKg = targetWeightKg,
                requestedPacePctPerWeek = pacePctPerWeek,
                plannedDailyEnergyKcal = plannedDailyEnergyKcal,
                minimumDailyEnergyKcal = ConstantsRegistry.floorKcal(profile.sex).toDouble(),
            ),
        )
    }
}
