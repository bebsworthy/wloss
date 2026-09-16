package app.wlo.core.engines

import kotlin.math.roundToInt

/**
 * Pure, shared goal-rung derivation for onboarding and the Weight surface.
 * Rungs are anchored to the journey start, while completion is always
 * recomputed from the canonical trend. Nothing is fired or persisted here:
 * edits, deletes, backfills, and revised goals therefore cannot leave stale
 * milestone state behind (WLO-0045).
 */
public object MilestoneLadder {
    public enum class State {
        COMPLETED,
        UPCOMING,
    }

    public data class Rung(
        public val weightKg: Double,
        public val isGoal: Boolean,
        public val state: State,
        /** Projected optimistic..pessimistic epoch-day range; null means unavailable. */
        public val rangeEpochDays: Pair<Long, Long>?,
    )

    /**
     * Builds a loss ladder with 4–8 rungs. Forecast bands are optional so
     * ordinary progress remains useful when date math is gated or held.
     */
    public fun loss(
        journeyStartKg: Double,
        currentTrendKg: Double,
        goalKg: Double,
        optimistic: ForecastBand? = null,
        pessimistic: ForecastBand? = null,
        forecastStartEpochDay: Long = 0L,
        stepDays: Int = STEP_DAYS,
    ): List<Rung> {
        val total = journeyStartKg - goalKg
        if (!total.isFinite() || total <= 0.0 || !currentTrendKg.isFinite()) return emptyList()
        val stepKg = stepFor(total)
        val count = (total / stepKg).toInt().coerceIn(MIN_RUNGS, MAX_RUNGS)
        return (1..count).map { index ->
            val candidate = journeyStartKg - stepKg * index
            val isGoal = index == count || candidate <= goalKg + EPSILON
            val weight = if (isGoal) goalKg else candidate
            Rung(
                weightKg = weight,
                isGoal = isGoal,
                state = if (currentTrendKg <= weight + EPSILON) State.COMPLETED else State.UPCOMING,
                rangeEpochDays =
                    if (currentTrendKg <= weight + EPSILON) {
                        null
                    } else {
                        crossingRange(
                            weightKg = weight,
                            optimistic = optimistic,
                            pessimistic = pessimistic,
                            startEpochDay = forecastStartEpochDay,
                            stepDays = stepDays,
                        )
                    },
            )
        }
    }

    /** 3–5 kg steps when they yield 4–8 rungs; otherwise quarter the journey. */
    public fun stepFor(totalKg: Double): Double =
        listOf(3.0, 4.0, 5.0)
            .firstOrNull { step -> (totalKg / step).toInt() in MIN_RUNGS..MAX_RUNGS }
            ?: (totalKg / (100.0 / QUARTER_PCT)).let { quarter ->
                (quarter * 100.0).roundToInt() / 100.0
            }

    public fun crossingRange(
        weightKg: Double,
        optimistic: ForecastBand?,
        pessimistic: ForecastBand?,
        startEpochDay: Long,
        stepDays: Int = STEP_DAYS,
    ): Pair<Long, Long>? {
        val fast = optimistic?.let { crossingDay(it, weightKg, startEpochDay, stepDays) } ?: return null
        val slow = pessimistic?.let { crossingDay(it, weightKg, startEpochDay, stepDays) } ?: return null
        return fast to slow.coerceAtLeast(fast)
    }

    private fun crossingDay(
        band: ForecastBand,
        weightKg: Double,
        startEpochDay: Long,
        stepDays: Int,
    ): Long? =
        band.trajectoryKg
            .indexOfFirst { it <= weightKg }
            .takeIf { it >= 0 }
            ?.let { index -> startEpochDay + (index + 1) * stepDays }

    public const val MIN_RUNGS: Int = 4
    public const val MAX_RUNGS: Int = 8
    public const val QUARTER_PCT: Double = 25.0
    public const val STEP_DAYS: Int = 7
    private const val EPSILON: Double = 1e-9
}
