package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.Provenance
import kotlinx.serialization.Serializable

/**
 * F07 "Transparent" engine (v1, R-A3) — pure, deterministic, instant-in/value-out
 * (D7: no clock, no IO, no globals; every `Instant`/epoch-day is a parameter).
 *
 * The math, in the open (F07 §3):
 *  - Trend consumption: F07 never smooths weight itself in production — it
 *    consumes F06's trend series. [ewma] exists as the reference smoother
 *    (R-A2 α, one smoother: F06 owns its only production use).
 *  - TDEE solve (closed form): `TDEE ≈ avg_daily_intake − weekly_trend_change_kg
 *    × 7700 ÷ 7` on a rolling [ConstantsRegistry.TDEE_WINDOW_DAYS]-day window.
 *  - Data-quality states (F07 §4, frozen): developing / updating / held gate
 *    everything; a held estimate is frozen, never silently published.
 */
public object EnergyEngine {
    public const val ENGINE_VERSION: String = ConstantsRegistry.ENERGY_ENGINE_VERSION
    public const val TDEE_FORMULA_VERSION: String = ConstantsRegistry.TDEE_FORMULA_VERSION

    /**
     * Exponentially weighted moving average with weight [alpha] (R-A2 default
     * [ConstantsRegistry.EWMA_ALPHA_DEFAULT]): `s_t = α·x_t + (1−α)·s_{t−1}`,
     * seeded `s_0 = x_0`. Zero-phase caveat: the first values lean on the seed
     * and recent values revise — published on the Algorithms page (F06 §4).
     *
     * Delegation only — the math lives in [SmoothingEngine.ewma] (one
     * implementation; smoothing has exactly one owner, F06).
     */
    public fun ewma(
        values: List<Double>,
        alpha: Double = ConstantsRegistry.EWMA_ALPHA_DEFAULT,
    ): List<Double> = SmoothingEngine.ewma(values, alpha)

    /**
     * Weekly pace (kg/week) between the first and last trend points of a
     * window, computed on the trend series (F07 §3: all pace math is on the
     * trend, never on raw weigh-ins). Returns null unless the window spans at
     * least [minSpanDays] days with finite values.
     */
    public fun paceKgPerWeek(
        firstTrendKg: Double,
        lastTrendKg: Double,
        spanDays: Long,
        minSpanDays: Long = 1,
    ): Double? {
        if (spanDays < minSpanDays) return null
        if (!firstTrendKg.isFinite() || !lastTrendKg.isFinite()) return null
        return (lastTrendKg - firstTrendKg) / spanDays * 7.0
    }

    /**
     * Closed-form TDEE solve over one [window] of daily energy records
     * (already sliced to the rolling window by the caller — the engine is
     * window-agnostic so the decomposition is reproducible by hand).
     *
     * `TDEE ≈ avg_daily_intake − (weekly_trend_change_kg × 7700 ÷ 7)` —
     * usable days are: intake present + weigh-in present, OR the day
     * explicitly Skipped/Fasted (intake contributes 0).
     */
    public fun measuredTdee(window: List<EnergyDay>): TdeeResult? {
        if (window.isEmpty()) return null
        val usable = window.filter { it.isUsable() }
        if (usable.isEmpty()) return null
        val intakeValues = usable.map { it.intakeKcal ?: 0.0 }
        val avgDailyIntake = intakeValues.average()

        val first = window.first()
        val last = window.last()
        val spanDays = last.epochDay - first.epochDay
        val weeklyChange =
            paceKgPerWeek(first.trendWeightKg ?: return null, last.trendWeightKg ?: return null, spanDays)
                ?: return null

        val tdee = avgDailyIntake - weeklyChange * ConstantsRegistry.KCAL_PER_KG_FAT / 7.0
        return TdeeResult(
            tdeeKcal = tdee,
            avgIntakeKcal = avgDailyIntake,
            weeklyTrendChangeKg = weeklyChange,
            usableDays = usable.size,
            windowDays = window.size,
            firstEpochDay = first.epochDay,
            lastEpochDay = last.epochDay,
        )
    }

    /** The TDEE result with its inputs spelled out for provenance/ledger use. */
    public fun TdeeResult.derivedProvenance(): Provenance.Derived =
        Provenance.Derived(
            formulaVersion = TDEE_FORMULA_VERSION,
            inputs =
                listOf(
                    "avgDailyIntakeKcal=$avgIntakeKcal",
                    "weeklyTrendChangeKg=$weeklyTrendChangeKg",
                    "usableDays=$usableDays",
                    "windowDays=$windowDays",
                    "kcalPerKgFat=${ConstantsRegistry.KCAL_PER_KG_FAT}",
                ),
        )

    /**
     * Data-quality state machine (F07 §4, frozen table). [window] is the
     * trailing [ConstantsRegistry.QUALITY_WINDOW_DAYS]-day record ending at
     * [lastEpochDay]; [userFlaggedAtypical] is the travel/illness marker.
     */
    public fun quality(
        window: List<EnergyDay>,
        lastEpochDay: Long,
        userFlaggedAtypical: Boolean = false,
    ): EngineState {
        val usableCount = window.count { it.isUsable() }
        if (usableCount < ConstantsRegistry.QUALITY_MIN_USABLE_DAYS) {
            return EngineState.Developing(usableDays = usableCount)
        }

        // Hold trigger 1: trailing days with no intake record AND no status mark.
        val unloggedRun = window.asReversed().takeWhile { !it.hasIntakeRecord && it.status == null }.size
        if (unloggedRun >= ConstantsRegistry.HOLD_UNLOGGED_DAYS) {
            return EngineState.Held(EngineHoldReason.UNLOGGED_DAYS, unloggedRun)
        }

        // Hold trigger 2: weigh-in gap longer than the threshold.
        val weighGapDays = gapSinceLastWeighIn(window, lastEpochDay)
        if (weighGapDays > ConstantsRegistry.HOLD_WEIGH_GAP_DAYS) {
            return EngineState.Held(EngineHoldReason.WEIGH_GAP, weighGapDays.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }

        // Hold trigger 3: user-flagged atypical week (travel/illness marker).
        if (userFlaggedAtypical) {
            return EngineState.Held(EngineHoldReason.USER_FLAGGED, null)
        }

        // Hold trigger 4: outlier screen — intake residual > 3σ on logged days.
        val logged = window.mapNotNull { it.intakeKcal }
        if (logged.size >= 3) {
            val mean = logged.average()
            val sigma = kotlin.math.sqrt(logged.sumOf { (it - mean) * (it - mean) } / logged.size)
            if (sigma > 0.0 && logged.any { kotlin.math.abs(it - mean) > ConstantsRegistry.HOLD_OUTLIER_SIGMA * sigma }) {
                return EngineState.Held(EngineHoldReason.OUTLIER, null)
            }
        }

        return EngineState.Updating(usableDays = usableCount)
    }

    private fun gapSinceLastWeighIn(
        window: List<EnergyDay>,
        lastEpochDay: Long,
    ): Long {
        val lastWeighIn = window.lastOrNull { it.trendWeightKg != null }?.epochDay ?: return Long.MAX_VALUE
        return lastEpochDay - lastWeighIn
    }
}

/** One daily energy record as F07 consumes it (F06/F02/F03 feed the series). */
@Serializable
public data class EnergyDay(
    public val epochDay: Long,
    /** Logged intake energy; null = no record. */
    public val intakeKcal: Double? = null,
    /** The one-tap day status (Logged/Skipped/Fasted), F02's day header. */
    public val status: DayStatus? = null,
    /** F06's trend-weight scalar for the day; null = no weigh-in. */
    public val trendWeightKg: Double? = null,
) {
    /** F07 §4: usable = (intake + weigh-in) or an explicit Skipped/Fasted mark. */
    public fun isUsable(): Boolean =
        (intakeKcal != null && trendWeightKg != null) ||
            status == DayStatus.SKIPPED ||
            status == DayStatus.FASTED

    /** True when any intake record or status mark exists for the day. */
    public val hasIntakeRecord: Boolean
        get() = intakeKcal != null || status != null
}

@Serializable
public enum class DayStatus {
    @kotlinx.serialization.SerialName("logged")
    LOGGED,

    @kotlinx.serialization.SerialName("skipped")
    SKIPPED,

    @kotlinx.serialization.SerialName("fasted")
    FASTED,
}

/** The closed-form solve output (F07 §3). */
@Serializable
public data class TdeeResult(
    public val tdeeKcal: Double,
    public val avgIntakeKcal: Double,
    public val weeklyTrendChangeKg: Double,
    public val usableDays: Int,
    public val windowDays: Int,
    public val firstEpochDay: Long,
    public val lastEpochDay: Long,
)

/** Data-quality states are domain values, never UI strings (FEATURES §2.1). */
@Serializable
public sealed interface EngineState {
    @Serializable
    public data class Developing(
        public val usableDays: Int,
    ) : EngineState

    @Serializable
    public data class Updating(
        public val usableDays: Int,
    ) : EngineState

    @Serializable
    public data class Held(
        public val reason: EngineHoldReason,
        public val magnitude: Int?,
    ) : EngineState
}

/** Machine-readable hold reasons (F07 §4); UI maps them to kind copy. */
@Serializable
public enum class EngineHoldReason(
    public val wireName: String,
) {
    @kotlinx.serialization.SerialName("unlogged-days")
    UNLOGGED_DAYS("unlogged-days"),

    @kotlinx.serialization.SerialName("weigh-gap")
    WEIGH_GAP("weigh-gap"),

    @kotlinx.serialization.SerialName("user-flagged")
    USER_FLAGGED("user-flagged"),

    @kotlinx.serialization.SerialName("outlier")
    OUTLIER("outlier"),
}
