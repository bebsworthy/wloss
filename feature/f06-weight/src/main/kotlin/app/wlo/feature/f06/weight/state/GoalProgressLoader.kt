package app.wlo.feature.f06.weight.state

import app.wlo.core.common.ClockPort
import app.wlo.core.common.WloResult
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.TargetsRepository
import app.wlo.core.engines.ColdStartInput
import app.wlo.core.engines.EnergyDay
import app.wlo.core.engines.EnergyEngine
import app.wlo.core.engines.EngineState
import app.wlo.core.engines.ForecastEngine
import app.wlo.core.engines.GoalForecastResult
import app.wlo.core.engines.MeasuredInput
import app.wlo.core.engines.MilestoneLadder
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Profile
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafety
import app.wlo.core.model.WeightGoalSafetyInput
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

/** Neutral, recomputed goal progress; milestone celebration belongs to WLO-0075. */
public data class GoalProgressUi(
    public val state: GoalProgressState,
    public val currentTrend: DerivedValue<Double>?,
    public val targetWeightKg: Double?,
    public val remainingKg: Double?,
    public val rungs: List<MilestoneLadder.Rung> = emptyList(),
    public val forecastCopy: String? = null,
)

public enum class GoalProgressState {
    NO_GOAL,
    UNAVAILABLE,
    TREND_FORMING,
    LOSS,
    MAINTENANCE,
    GAIN,
}

/**
 * Joins Targets, forecast-quality evidence, and the canonical trend at one
 * feature boundary. The UI receives one honest snapshot and never rebuilds
 * goal or forecast policy itself.
 */
public class GoalProgressLoader(
    private val clock: ClockPort,
    private val targets: TargetsRepository,
    private val dayProjection: DayProjectionRepository,
    private val readGoalSafetyInput: suspend (String) -> WeightGoalSafetyInput?,
    private val zoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    public suspend fun load(
        profile: Profile,
        canonical: DerivedValue<Double>?,
        today: Long,
    ): GoalProgressUi {
        val record =
            when (val result = targets.current(profile.id)) {
                is WloResult.Ok -> result.value
                is WloResult.Err ->
                    return GoalProgressUi(
                        GoalProgressState.UNAVAILABLE,
                        canonical,
                        null,
                        null,
                    )
            } ?: return GoalProgressUi(GoalProgressState.NO_GOAL, canonical, null, null)
        val goal = record.document.goal
        val targetKg = goal.targetWeightKg
        val trendKg =
            canonical?.value
                ?: return GoalProgressUi(GoalProgressState.TREND_FORMING, null, targetKg, null)
        val attestation = readGoalSafetyInput(profile.id)
        val mode =
            attestation?.mode ?: when {
                targetKg < trendKg -> WeightGoalMode.LOSS
                targetKg > trendKg -> WeightGoalMode.GAIN
                else -> WeightGoalMode.MAINTENANCE
            }
        val remaining = abs(targetKg - trendKg)
        if (mode == WeightGoalMode.MAINTENANCE) {
            return GoalProgressUi(
                state = GoalProgressState.MAINTENANCE,
                currentTrend = canonical,
                targetWeightKg = targetKg,
                remainingKg = remaining,
                forecastCopy = "Maintenance has no finish date — the target is the range you are holding.",
            )
        }
        if (mode == WeightGoalMode.GAIN) {
            return GoalProgressUi(
                state = GoalProgressState.GAIN,
                currentTrend = canonical,
                targetWeightKg = targetKg,
                remainingKg = remaining,
                forecastCopy =
                    "Gain progress is tracked without dates until direction-aware forecasting is available.",
            )
        }

        val intake =
            record.document.energy.budgetKcal
                ?: record.document.energy.weeklyBudgetKcal
                    ?.div(DAYS_PER_WEEK)
        val age = profile.birthYear?.let { clock.now().toLocalDateTime(zoneProvider()).year - it }
        val height = profile.heightCm
        val eligibility =
            WeightGoalSafety.evaluate(
                (attestation ?: WeightGoalSafetyInput(ageYears = age, mode = mode, currentWeightKg = trendKg)).copy(
                    ageYears = age,
                    mode = mode,
                    currentWeightKg = trendKg,
                    targetWeightKg = targetKg,
                    requestedPacePctPerWeek = goal.pacePctPerWeek,
                    plannedDailyEnergyKcal = intake,
                    minimumDailyEnergyKcal = record.document.energy.floorKcal,
                ),
            )
        val coldStart =
            if (age != null && height != null && intake != null) {
                ColdStartInput(
                    sex = profile.sex,
                    ageYears = age,
                    heightCm = height,
                    startTrendKg = trendKg,
                    goalWeightKg = targetKg,
                    activityLevel = profile.activityLevel,
                    intakeKcal = intake,
                    startEpochDay = today,
                    startInstant = clock.now(),
                )
            } else {
                null
            }
        val forecastResult = coldStart?.let { forecast(profile, today, eligibility, it) }
        val result = (forecastResult as? ForecastRead.Success)?.result
        val bands =
            when (result) {
                is GoalForecastResult.Available -> result.bands
                is GoalForecastResult.Developing -> result.bands
                is GoalForecastResult.Held,
                is GoalForecastResult.Withheld,
                null,
                -> null
            }
        val startKg = maxOf(profile.startWeightKg ?: trendKg, trendKg)
        val rungs =
            MilestoneLadder.loss(
                journeyStartKg = startKg,
                currentTrendKg = trendKg,
                goalKg = targetKg,
                optimistic = bands?.optimistic,
                pessimistic = bands?.pessimistic,
                forecastStartEpochDay = today,
            )
        return GoalProgressUi(
            state = GoalProgressState.LOSS,
            currentTrend = canonical,
            targetWeightKg = targetKg,
            remainingKg = (trendKg - targetKg).coerceAtLeast(0.0),
            rungs = rungs,
            forecastCopy =
                if (forecastResult is ForecastRead.Failed) {
                    "Dates couldn't refresh; weight progress still works."
                } else {
                    forecastCopy(result)
                },
        )
    }

    private suspend fun forecast(
        profile: Profile,
        today: Long,
        eligibility: app.wlo.core.model.WeightGoalEligibility,
        input: ColdStartInput,
    ): ForecastRead {
        val window =
            when (
                val result =
                    dayProjection.range(
                        profile.id,
                        today - ConstantsRegistry.QUALITY_WINDOW_DAYS + 1,
                        today,
                    )
            ) {
                is WloResult.Ok -> result.value
                is WloResult.Err -> return ForecastRead.Failed
            }
        val energyDays =
            window.map { day ->
                EnergyDay(
                    epochDay = day.dayEpochDay,
                    intakeKcal = day.intakeKcal?.value,
                    trendWeightKg = day.trendWeightKg?.value,
                )
            }
        val engineState = EnergyEngine.quality(energyDays, today)
        val measured =
            if (engineState is EngineState.Updating) {
                EnergyEngine
                    .measuredTdee(
                        energyDays.filter { it.epochDay >= today - ConstantsRegistry.TDEE_WINDOW_DAYS + 1 },
                    )?.let { solved ->
                        MeasuredInput(
                            sex = profile.sex,
                            ageYears = input.ageYears,
                            heightCm = input.heightCm,
                            startTrendKg = input.startTrendKg,
                            goalWeightKg = input.goalWeightKg,
                            intakeKcal = input.intakeKcal,
                            measuredTdeeKcal = solved.tdeeKcal,
                            startEpochDay = today,
                            startInstant = clock.now(),
                        )
                    }
            } else {
                null
            }
        return ForecastRead.Success(ForecastEngine.evaluate(input, eligibility, engineState, measured))
    }

    private fun forecastCopy(result: GoalForecastResult?): String =
        when (result) {
            is GoalForecastResult.Available ->
                "Ranges use your logged-data forecast; they update when the trend changes."
            is GoalForecastResult.Developing ->
                "Estimated ranges while your logged-data forecast develops " +
                    "(${result.usableDays}/${result.requiredUsableDays} usable days)."
            is GoalForecastResult.Held ->
                "Dates are paused until the forecast has reliable data again."
            is GoalForecastResult.Withheld ->
                "Dates are hidden by the goal safety gate; weight progress still works."
            null ->
                "Dates need complete profile and plan inputs; weight progress still works."
        }

    private sealed interface ForecastRead {
        data class Success(
            val result: GoalForecastResult,
        ) : ForecastRead

        data object Failed : ForecastRead
    }

    private companion object {
        const val DAYS_PER_WEEK: Double = 7.0
    }
}
