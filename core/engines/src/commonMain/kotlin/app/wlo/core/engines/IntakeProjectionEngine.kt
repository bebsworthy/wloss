package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.Provenance
import app.wlo.core.model.Sex
import kotlinx.datetime.Instant
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/** Exploration at an unchanged intake. A goal changes the viewing horizon, never the energy equation. */
public object IntakeProjectionEngine {
    public const val MODEL_VERSION: String = "intake-scenario-v2"
    public const val WEEKS: Int = 26

    /** Goal-independent: the caller may annotate any target without changing the projected path. */
    public fun project(
        input: IntakeProjectionInput,
        targetKg: Double? = null,
        horizonOverride: Int? = null,
    ): IntakeProjection? {
        val positiveInputs =
            listOf(input.heightCm, input.startWeightKg, input.maintenanceKcal)
                .all { it.isFinite() && it > 0 }
        val validIntake = input.intakeKcal.isFinite() && input.intakeKcal >= 0
        if (input.ageYears < 18 || !positiveInputs || !validIntake) return null
        val horizon = horizonOverride?.coerceIn(1, MAX_WEEKS) ?: horizon(input, targetKg)
        val expected = trajectory(input, 1.0, horizon) ?: return null
        val fast = trajectory(input, ConstantsRegistry.COLD_START_BAND_FAST_FACTOR, horizon) ?: return null
        val slow = trajectory(input, ConstantsRegistry.COLD_START_BAND_SLOW_FACTOR, horizon) ?: return null
        val provenance =
            if (input.mode == ForecastMode.COLD_START) {
                Provenance.Estimated(input.at, "$MODEL_VERSION/${ConstantsRegistry.BMR_FORMULA_VERSION}", null)
            } else {
                Provenance.Derived(
                    MODEL_VERSION,
                    listOf(
                        "maintenanceKcal=${input.maintenanceKcal}",
                        "intakeKcal=${input.intakeKcal}",
                        "startWeightKg=${input.startWeightKg}",
                        "horizonWeeks=$horizon",
                        "sensitivity=existing-cold-start-pace-factors;not-calibrated-confidence",
                    ),
                )
            }
        return IntakeProjection(
            expected.indices.map { minOf(fast[it], slow[it], expected[it]) },
            expected,
            expected.indices.map { maxOf(fast[it], slow[it], expected[it]) },
            input.mode,
            provenance,
            equilibriumKg = equilibrium(input),
            horizonLimited = horizon == MAX_WEEKS,
            projectedTargetKg = targetKg,
            goalWindow =
                targetKg?.let {
                    arrival(input, it, ConstantsRegistry.COLD_START_BAND_FAST_FACTOR) to
                        arrival(input, it, ConstantsRegistry.COLD_START_BAND_SLOW_FACTOR)
                },
        )
    }

    // Numerical guard, not a promise that century-long projections are reliable.
    private const val MAX_WEEKS = 5200

    private fun feedback(input: IntakeProjectionInput): Double =
        if (input.intakeKcal > input.maintenanceKcal) {
            ConstantsRegistry.GAIN_EXPENDITURE_FEEDBACK_KCAL_PER_DAY_PER_KG
        } else {
            ForecastEngine.bmrMifflinStJeor(input.sex, input.startWeightKg + 1, input.heightCm, input.ageYears) -
                ForecastEngine.bmrMifflinStJeor(input.sex, input.startWeightKg, input.heightCm, input.ageYears)
        }

    private fun equilibrium(input: IntakeProjectionInput): Double =
        input.startWeightKg + (input.intakeKcal - input.maintenanceKcal) / feedback(input)

    /** Arrival is computed independently of the displayed horizon, including while a drag holds the axis. */
    private fun arrival(
        input: IntakeProjectionInput,
        target: Double,
        scale: Double,
    ): Double? {
        val limit = equilibrium(input)
        val ratio = (target - limit) / (input.startWeightKg - limit)
        if (!ratio.isFinite() || ratio <= 0 || ratio >= 1) return null
        val q = 1 - feedback(input) * 7 / ConstantsRegistry.KCAL_PER_KG_FAT * scale
        val week = ceil(ln(ratio) / ln(q))
        if (!week.isFinite() || week > MAX_WEEKS || week < 1) return null
        val before = limit + (input.startWeightKg - limit) * q.pow(week - 1)
        val after = limit + (input.startWeightKg - limit) * q.pow(week)
        return (week - 1 + (target - before) / (after - before)).takeIf { it.isFinite() && it >= 0 }
    }

    private fun horizon(
        input: IntakeProjectionInput,
        targetKg: Double?,
    ): Int {
        if (targetKg == null || !targetKg.isFinite() || targetKg <= 0 || targetKg == input.startWeightKg) return WEEKS
        val balance = input.intakeKcal - input.maintenanceKcal
        if ((targetKg - input.startWeightKg) * balance <= 0) return WEEKS
        val limit = equilibrium(input)
        val ratio = (targetKg - limit) / (input.startWeightKg - limit)
        // An unreachable goal still gets a longer view of the leveling trajectory.
        if (ratio <= 0 || ratio >= 1) return 104
        val q =
            1 - feedback(input) * 7 / ConstantsRegistry.KCAL_PER_KG_FAT *
                ConstantsRegistry.COLD_START_BAND_SLOW_FACTOR
        val late = ln(ratio) / ln(q)
        return (ceil(late * 1.1 / 13) * 13).coerceIn(13.0, MAX_WEEKS.toDouble()).toInt()
    }

    private fun trajectory(
        input: IntakeProjectionInput,
        scale: Double,
        weeks: Int,
    ): List<Double>? {
        val start = input.startWeightKg
        val bmr = ForecastEngine.bmrMifflinStJeor(input.sex, start, input.heightCm, input.ageYears)
        val gaining = input.intakeKcal > input.maintenanceKcal
        val points = mutableListOf(start)
        repeat(weeks) {
            val weight = points.last()
            // Same expenditure feedback as ForecastEngine, held adjustment and 7,700 kcal/kg model.
            val expenditure =
                if (gaining) {
                    input.maintenanceKcal + ConstantsRegistry.GAIN_EXPENDITURE_FEEDBACK_KCAL_PER_DAY_PER_KG * (weight - start)
                } else {
                    input.maintenanceKcal + ForecastEngine.bmrMifflinStJeor(input.sex, weight, input.heightCm, input.ageYears) - bmr
                }
            val balance = if (gaining) max(input.intakeKcal - expenditure, 0.0) else input.intakeKcal - expenditure
            val next = weight + balance * 7 / ConstantsRegistry.KCAL_PER_KG_FAT * scale
            // Do not clamp or fabricate a physically impossible continuation for extreme manual inputs.
            if (!next.isFinite() || next <= 0) return null
            points += next
        }
        return points
    }
}

public data class IntakeProjectionInput(
    val sex: Sex?,
    val ageYears: Int,
    val heightCm: Double,
    val startWeightKg: Double,
    val maintenanceKcal: Double,
    val intakeKcal: Double,
    val mode: ForecastMode,
    val at: Instant,
)

/** Weekly samples including today. Lower/upper mean body mass, never goal-dependent optimism. */
public data class IntakeProjection(
    val lowerKg: List<Double>,
    val expectedKg: List<Double>,
    val upperKg: List<Double>,
    val mode: ForecastMode,
    val provenance: Provenance,
    val equilibriumKg: Double? = null,
    val horizonLimited: Boolean = false,
    val projectedTargetKg: Double? = null,
    val goalWindow: Pair<Double?, Double?>? = null,
) {
    public val weeks: Int get() = expectedKg.lastIndex

    /** First target crossing per outer path, interpolated without stopping or changing the paths. */
    public fun crossingWeeks(targetKg: Double): Pair<Double?, Double?> {
        if (targetKg == projectedTargetKg && goalWindow != null) return goalWindow
        val start = expectedKg.first()
        if (targetKg == start || !targetKg.isFinite()) return null to null

        fun crossing(points: List<Double>): Double? {
            for (i in 1 until points.size) {
                val from = points[i - 1]
                val to = points[i]
                if (to != from && (targetKg - from) * (targetKg - to) <= 0) {
                    return i - 1 + (targetKg - from) / (to - from)
                }
            }
            return null
        }
        val lower = crossing(lowerKg)
        val upper = crossing(upperKg)
        return if (targetKg < start) lower to upper else upper to lower
    }
}
