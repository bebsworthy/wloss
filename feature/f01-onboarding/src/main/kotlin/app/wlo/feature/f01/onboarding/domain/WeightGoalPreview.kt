package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.engines.ColdStartInput
import app.wlo.core.engines.EngineState
import app.wlo.core.engines.ForecastEngine
import app.wlo.core.engines.GoalForecastResult
import app.wlo.core.engines.MeasuredInput
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.Sex
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import kotlinx.datetime.Instant
import kotlin.math.abs

/**
 * One pure goal-preview boundary shared by first-run and the later Goals editor.
 * It owns date-to-pace conversion, safety eligibility, and forecast quality
 * semantics so the two entry points cannot quietly diverge (WLO-0074).
 */
public object WeightGoalPreview {
    public fun evaluate(input: WeightGoalPreviewInput): WeightGoalPreviewResult {
        val impliedPace = impliedPacePctPerWeek(input)
        val effectivePace =
            when (input.mode) {
                WeightGoalMode.MAINTENANCE -> 0.0
                else -> impliedPace ?: input.requestedPacePctPerWeek
            }
        val eligibility =
            GoalEditorSafety.evaluate(
                ageYears = input.ageYears,
                currentWeightKg = input.currentWeightKg,
                sex = input.sex,
                mode = input.mode,
                targetWeightKg = input.targetWeightKg,
                pacePctPerWeek = effectivePace,
                plannedDailyEnergyKcal = input.plannedDailyEnergyKcal,
                pregnant = input.pregnant,
                breastfeeding = input.breastfeeding,
                eatingDisorderConcern = input.eatingDisorderConcern,
                medicallyInfluencedWeight = input.medicallyInfluencedWeight,
            )
        val coldStart = input.coldStartInput()
        val forecast =
            if (coldStart == null) {
                null
            } else {
                ForecastEngine.evaluate(
                    coldStartInput = coldStart,
                    eligibility = eligibility,
                    engineState = input.engineState,
                    measuredInput = input.measuredInput,
                    lastGoodBands = input.lastGoodBands,
                )
            }
        return WeightGoalPreviewResult(
            eligibility = eligibility,
            forecastEligibility = ForecastEngine.eligibilityForForecast(eligibility),
            effectivePacePctPerWeek = effectivePace,
            impliedPacePctPerWeek = impliedPace,
            targetDateValid = input.targetEpochDay == null || input.targetEpochDay > input.todayEpochDay,
            forecast = forecast,
            missingForecastInputs = coldStart == null,
        )
    }

    private fun impliedPacePctPerWeek(input: WeightGoalPreviewInput): Double? {
        val targetDay = input.targetEpochDay ?: return null
        val current = input.currentWeightKg ?: return null
        val target = input.targetWeightKg ?: return null
        val days = targetDay - input.todayEpochDay
        if (days <= 0L || current <= 0.0) return null
        val weeks = days / 7.0
        return abs(target - current) / current * 100.0 / weeks
    }

    private fun WeightGoalPreviewInput.coldStartInput(): ColdStartInput? {
        val age = ageYears?.takeIf { it >= 18 } ?: return null
        val height = heightCm?.takeIf { it > 0.0 } ?: return null
        val current = currentWeightKg?.takeIf { it > 0.0 } ?: return null
        val target = targetWeightKg?.takeIf { it > 0.0 } ?: return null
        val intake = plannedDailyEnergyKcal?.takeIf { it > 0.0 } ?: return null
        return ColdStartInput(
            sex = sex,
            ageYears = age,
            heightCm = height,
            startTrendKg = current,
            goalWeightKg = target,
            activityLevel = activityLevel,
            intakeKcal = intake,
            startEpochDay = todayEpochDay,
            startInstant = now,
        )
    }
}

public data class WeightGoalPreviewInput(
    public val ageYears: Int?,
    public val sex: Sex?,
    public val heightCm: Double?,
    public val activityLevel: ActivityLevel,
    public val currentWeightKg: Double?,
    public val targetWeightKg: Double?,
    public val mode: WeightGoalMode,
    public val requestedPacePctPerWeek: Double?,
    public val targetEpochDay: Long?,
    public val plannedDailyEnergyKcal: Double?,
    public val pregnant: SafetyAnswer,
    public val breastfeeding: SafetyAnswer,
    public val eatingDisorderConcern: SafetyAnswer,
    public val medicallyInfluencedWeight: SafetyAnswer,
    public val todayEpochDay: Long,
    public val now: Instant,
    public val engineState: EngineState = EngineState.Developing(0),
    public val measuredInput: MeasuredInput? = null,
    public val lastGoodBands: app.wlo.core.engines.ForecastBands? = null,
)

public data class WeightGoalPreviewResult(
    public val eligibility: WeightGoalEligibility,
    public val forecastEligibility: WeightGoalEligibility,
    public val effectivePacePctPerWeek: Double?,
    public val impliedPacePctPerWeek: Double?,
    public val targetDateValid: Boolean,
    public val forecast: GoalForecastResult?,
    public val missingForecastInputs: Boolean,
)
