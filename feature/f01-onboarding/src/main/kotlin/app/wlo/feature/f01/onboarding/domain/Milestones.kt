package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.engines.ForecastBand
import kotlin.math.roundToInt

/**
 * Milestone auto-breakdown (F01 §3): the goal split into trend-based rungs —
 * "every ~25 % of the journey or every 3–5 kg, whichever yields 4–8 rungs".
 * Each rung's projection is a DATE RANGE from the F07 band math (crossing
 * day per band), re-projected as data arrives. A rung whose band data does
 * not reach it carries NO date at all — projections are hidden, never guessed
 * (F01 §4 data-quality gating).
 */
public object Milestones {
    public data class Rung(
        public val weightKg: Double,
        public val isGoal: Boolean,
        /** Projected day range (optimistic..pessimistic epoch days); null = not projectable. */
        public val rangeEpochDays: Pair<Long, Long>?,
    )

    /**
     * Builds the ladder: [from] current trend, [to] goal. The goal rung always
     * lands last; rung spacing prefers 3/4/5 kg (spec's 3–5 kg band) when that
     * yields 4–8 rungs, else quarters the journey (25 % rule).
     */
    public fun ladder(
        from: Double,
        to: Double,
        optimistic: ForecastBand,
        pessimistic: ForecastBand,
        startEpochDay: Long,
        stepDays: Int = STEP_DAYS,
    ): List<Rung> {
        val total = from - to
        if (total <= 0.0) return emptyList()
        val stepKg = stepFor(total)
        val count = (total / stepKg).toInt().coerceIn(MIN_RUNGS, MAX_RUNGS)
        val weights =
            (1..count).map { index ->
                val w = from - stepKg * index
                if (index == count) to else w
            }
        return weights.map { weight ->
            val isGoal = weight <= to + 1e-9
            Rung(
                weightKg = if (isGoal) to else weight,
                isGoal = isGoal,
                rangeEpochDays =
                    crossingRange(
                        weight = if (isGoal) to else weight,
                        optimistic = optimistic,
                        pessimistic = pessimistic,
                        startEpochDay = startEpochDay,
                        stepDays = stepDays,
                    ),
            )
        }
    }

    /** 3–5 kg steps when they land in the 4–8 rung window; else the 25 % quartering. */
    public fun stepFor(totalKg: Double): Double =
        listOf(3.0, 4.0, 5.0)
            .firstOrNull { step ->
                val n = (totalKg / step).toInt()
                n in MIN_RUNGS..MAX_RUNGS
            }
            ?: (totalKg / (100.0 / QUARTER_PCT)).let { q -> (q * 100.0).roundToInt() / 100.0 }

    /**
     * The day each band's trajectory crosses [weight]; range = optimistic..pessimistic.
     * Any band that never reaches the rung (beyond horizon) voids the range —
     * hidden, not approximated.
     */
    public fun crossingRange(
        weight: Double,
        optimistic: ForecastBand,
        pessimistic: ForecastBand,
        startEpochDay: Long,
        stepDays: Int = STEP_DAYS,
    ): Pair<Long, Long>? {
        val fast = crossingDay(optimistic, weight, startEpochDay, stepDays) ?: return null
        val slow = crossingDay(pessimistic, weight, startEpochDay, stepDays) ?: return null
        return fast to slow.coerceAtLeast(fast)
    }

    private fun crossingDay(
        band: ForecastBand,
        weightKg: Double,
        startEpochDay: Long,
        stepDays: Int,
    ): Long? {
        // Trajectory[0] is after step 1; walk the steps until the path crosses.
        val trajectory = band.trajectoryKg
        if (trajectory.isEmpty()) return null
        for ((index, value) in trajectory.withIndex()) {
            if (value <= weightKg) {
                return startEpochDay + (index + 1) * stepDays
            }
        }
        return null
    }

    public const val MIN_RUNGS: Int = 4
    public const val MAX_RUNGS: Int = 8
    public const val QUARTER_PCT: Double = 25.0
    public const val STEP_DAYS: Int = 7
}
