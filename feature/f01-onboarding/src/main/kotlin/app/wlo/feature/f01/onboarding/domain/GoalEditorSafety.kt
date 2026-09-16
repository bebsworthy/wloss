package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.Sex
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafety
import app.wlo.core.model.WeightGoalSafetyInput

/** Pure adapter from the existing editor fields to the one shared safety contract. */
public object GoalEditorSafety {
    public fun evaluate(
        ageYears: Int?,
        currentWeightKg: Double?,
        sex: Sex?,
        mode: WeightGoalMode,
        targetWeightKg: Double?,
        pacePctPerWeek: Double?,
        plannedDailyEnergyKcal: Double?,
        pregnant: SafetyAnswer,
        breastfeeding: SafetyAnswer,
        eatingDisorderConcern: SafetyAnswer,
        medicallyInfluencedWeight: SafetyAnswer,
    ): WeightGoalEligibility =
        WeightGoalSafety.evaluate(
            input(
                ageYears = ageYears,
                currentWeightKg = currentWeightKg,
                sex = sex,
                mode = mode,
                targetWeightKg = targetWeightKg,
                pacePctPerWeek = pacePctPerWeek,
                plannedDailyEnergyKcal = plannedDailyEnergyKcal,
                pregnant = pregnant,
                breastfeeding = breastfeeding,
                eatingDisorderConcern = eatingDisorderConcern,
                medicallyInfluencedWeight = medicallyInfluencedWeight,
            ),
        )

    public fun input(
        ageYears: Int?,
        currentWeightKg: Double?,
        sex: Sex?,
        mode: WeightGoalMode,
        targetWeightKg: Double?,
        pacePctPerWeek: Double?,
        plannedDailyEnergyKcal: Double?,
        pregnant: SafetyAnswer,
        breastfeeding: SafetyAnswer,
        eatingDisorderConcern: SafetyAnswer,
        medicallyInfluencedWeight: SafetyAnswer,
    ): WeightGoalSafetyInput =
        WeightGoalSafetyInput(
            ageYears = ageYears,
            mode = mode,
            currentWeightKg = currentWeightKg,
            targetWeightKg = targetWeightKg,
            requestedPacePctPerWeek = if (mode == WeightGoalMode.MAINTENANCE) 0.0 else pacePctPerWeek,
            plannedDailyEnergyKcal = plannedDailyEnergyKcal,
            minimumDailyEnergyKcal = ConstantsRegistry.floorKcal(sex).toDouble(),
            pregnant = pregnant,
            breastfeeding = breastfeeding,
            eatingDisorderConcern = eatingDisorderConcern,
            medicallyInfluencedWeight = medicallyInfluencedWeight,
        )
}
