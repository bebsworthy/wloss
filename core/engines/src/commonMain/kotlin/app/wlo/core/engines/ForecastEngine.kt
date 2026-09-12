package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.Provenance
import app.wlo.core.model.Sex
import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * F07 3-band goal forecast with deceleration (R-A5: cold-start mode is
 * mandatory in v1; R-A6: partition-free — the single 7,700 kcal/kg constant,
 * caveat near goal). Pure and deterministic (D7): all time enters as epoch
 * days; the finish dates are epoch days, rendering is the caller's job.
 *
 * Deceleration core (F07 §3): future TDEE is re-estimated at future bodyweight —
 * BMR falls with weight, the Adjustment term is held, activity follows the
 * steps baseline — so the deficit shrinks along the path and the projected
 * weekly rate decays instead of staying constant.
 */
public object ForecastEngine {
    public const val MODEL_VERSION: String = ConstantsRegistry.FORECAST_MODEL_VERSION

    /**
     * Mifflin-St Jeor BMR (F07 §3 BMR term, F01 §3 formula estimate):
     * `10·kg + 6.25·cm − 5·age + 5` (male) / `− 161` (female).
     * DEFAULTED (not fixed by spec): undisclosed/other sex uses the midpoint
     * correction `− 78` — published here, amendable in one place.
     */
    public fun bmrMifflinStJeor(
        sex: Sex?,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
    ): Double {
        require(weightKg > 0.0 && heightCm > 0.0 && ageYears >= 0) {
            "weight/height/age must be positive"
        }
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return when (sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
            null, Sex.OTHER -> base - 78.0
        }
    }

    /**
     * Cold-start forecast (R-A5 [v1] mandatory): formula-BMR-based, wide bands,
     * `ESTIMATED` chip, "will sharpen as you log". TDEE estimate =
     * BMR × activity multiplier (F01 §3); adjustment term starts at 0.
     */
    public fun coldStart(input: ColdStartInput): ForecastBands {
        val bmrAtStart = bmrMifflinStJeor(input.sex, input.startTrendKg, input.heightCm, input.ageYears)
        val tdeeEstimate =
            bmrAtStart * ConstantsRegistry.activityMultiplier(input.activityLevel)
        val expectedPace = paceOf(tdeeEstimate - input.intakeKcal)
        val bands =
            integrateBands(
                input = input,
                tdeeAtStartKcal = tdeeEstimate,
                stepsPerDay = null,
                fastScale = ConstantsRegistry.COLD_START_BAND_FAST_FACTOR,
                slowScale = ConstantsRegistry.COLD_START_BAND_SLOW_FACTOR,
                expectedPaceKgPerWeek = expectedPace,
            )
        return ForecastBands(
            mode = ForecastMode.COLD_START,
            engineVersion = EnergyEngine.ENGINE_VERSION,
            modelVersion = MODEL_VERSION,
            bmrVersion = ConstantsRegistry.BMR_FORMULA_VERSION,
            tdeeEstimateKcal = tdeeEstimate,
            optimistic = bands.fast,
            expected = bands.expected,
            pessimistic = bands.slow,
            provenance =
                Provenance.Estimated(
                    at = input.startInstant,
                    method = "cold-start/${ConstantsRegistry.BMR_FORMULA_VERSION}",
                    confidence = null,
                ),
        )
    }

    /**
     * Measured-mode forecast: expected band integrates the decaying rate from
     * the current measured-TDEE point estimate; optimistic/pessimistic come
     * from the trailing pace distribution (80th/20th percentile over the
     * 28-day window) crossed with the TDEE uncertainty — v1 approximates the
     * crossing by scaling the initial deficit with the pace ratio (published
     * on the Algorithms page). Returns null when no measured TDEE exists yet.
     */
    public fun measured(input: MeasuredInput): ForecastBands? {
        val tdee = input.measuredTdeeKcal ?: return null
        val expectedPace = paceOf(tdee - input.intakeKcal)
        val fastScale =
            input.fastPaceKgPerWeek
                ?.takeIf { expectedPace > 0 && it > 0 }
                ?.let { it / expectedPace }
                ?: ConstantsRegistry.COLD_START_BAND_FAST_FACTOR
        val slowScale =
            input.slowPaceKgPerWeek
                ?.takeIf { expectedPace > 0 && it > 0 }
                ?.let { it / expectedPace }
                ?: ConstantsRegistry.COLD_START_BAND_SLOW_FACTOR

        val stepsPerDay = input.typicalStepsPerDay
        val activityKcalPerDay = stepsPerDay?.let { activityKcal(it, input.startTrendKg) }
        val bands =
            integrateBands(
                input = input.common(),
                tdeeAtStartKcal = tdee,
                stepsPerDay = stepsPerDay,
                fastScale = fastScale,
                slowScale = slowScale,
                expectedPaceKgPerWeek = expectedPace,
            )
        return ForecastBands(
            mode = ForecastMode.MEASURED,
            engineVersion = EnergyEngine.ENGINE_VERSION,
            modelVersion = MODEL_VERSION,
            bmrVersion = ConstantsRegistry.BMR_FORMULA_VERSION,
            tdeeEstimateKcal = tdee,
            optimistic = bands.fast,
            expected = bands.expected,
            pessimistic = bands.slow,
            provenance =
                Provenance.Derived(
                    formulaVersion = MODEL_VERSION,
                    inputs =
                        listOf(
                            "measuredTdeeKcal=$tdee",
                            "intakeKcal=${input.intakeKcal}",
                            "startTrendKg=${input.startTrendKg}",
                            "fastPaceKgPerWeek=${input.fastPaceKgPerWeek}",
                            "slowPaceKgPerWeek=${input.slowPaceKgPerWeek}",
                        ),
                ),
        )
    }

    /** Mass-scaled steps → kcal/day (published linear fit, Algorithms page). */
    public fun activityKcal(
        stepsPerDay: Int,
        weightKg: Double,
    ): Double = stepsPerDay * ConstantsRegistry.KCAL_PER_STEP_PER_KG * weightKg

    // --- integration core -------------------------------------------------

    private fun integrateBands(
        input: ColdStartInput,
        tdeeAtStartKcal: Double,
        stepsPerDay: Int?,
        fastScale: Double,
        slowScale: Double,
        expectedPaceKgPerWeek: Double,
    ): Bands {
        val activityKcal = stepsPerDay?.let { activityKcal(it, input.startTrendKg) } ?: 0.0
        // The Adjustment term is held along the path (F07 §3): it absorbs
        // formula error and adaptation; the deceleration comes from BMR only.
        val adjustment = tdeeAtStartKcal - bmrAt(input, input.startTrendKg) - activityKcal

        val expected =
            integrate(
                input,
                adjustment,
                activityKcal,
                scale = 1.0,
            )
        val fast =
            if (expectedPaceKgPerWeek <= 0.0) {
                // No deficit on trend: the optimistic band is a straight hold.
                integrate(input, adjustment, activityKcal, scale = 0.0)
            } else {
                integrate(input, adjustment, activityKcal, scale = max(fastScale, 0.0))
            }
        val slow =
            if (expectedPaceKgPerWeek <= 0.0) {
                integrate(input, adjustment, activityKcal, scale = 0.0)
            } else {
                integrate(input, adjustment, activityKcal, scale = max(slowScale, 0.0))
            }
        return Bands(fast = fast, expected = expected, slow = slow)
    }

    /**
     * Integrates the decaying weekly rate in [ConstantsRegistry.FORECAST_STEP_DAYS]
     * steps until goal weight, or [ConstantsRegistry.FORECAST_HORIZON_WEEKS].
     */
    private fun integrate(
        input: ColdStartInput,
        adjustment: Double,
        activityKcal: Double,
        scale: Double,
    ): ForecastBand {
        val stepDays = ConstantsRegistry.FORECAST_STEP_DAYS
        val stepFractionOfWeek = stepDays / 7.0
        val rates = ArrayList<Double>()
        val trajectory = ArrayList<Double>()
        var weight = input.startTrendKg
        var epochDay = input.startEpochDay
        var steps = 0
        val maxSteps = ConstantsRegistry.FORECAST_HORIZON_WEEKS * 7 / stepDays

        if (input.goalWeightKg >= input.startTrendKg) {
            // v1 models loss paths; an at/below-start goal is already met.
            return ForecastBand(finishEpochDay = input.startEpochDay, weeklyRatesKg = emptyList(), trajectoryKg = emptyList())
        }

        while (steps < maxSteps) {
            val tdeeNow = bmrAt(input, weight) + activityKcal + adjustment
            val deficit = tdeeNow - input.intakeKcal
            val rate = paceOf(deficit) * scale
            rates += rate
            val next = weight - rate * stepFractionOfWeek
            epochDay += stepDays
            steps += 1
            if (next <= input.goalWeightKg) {
                // Interpolate the exact finish inside the final step; the cone
                // lands ON the goal, never past it.
                trajectory += input.goalWeightKg
                val overshoot = input.goalWeightKg - weight
                val fraction =
                    if (rate > 0.0) (-overshoot / (rate * stepFractionOfWeek)).coerceIn(0.0, 1.0) else 1.0
                val finishDay = (epochDay - stepDays + (fraction * stepDays).toLong())
                return ForecastBand(finishEpochDay = finishDay, weeklyRatesKg = rates, trajectoryKg = trajectory)
            }
            weight = next
            trajectory += weight
        }
        return ForecastBand(finishEpochDay = null, weeklyRatesKg = rates, trajectoryKg = trajectory)
    }

    private fun bmrAt(
        input: ColdStartInput,
        weightKg: Double,
    ): Double = bmrMifflinStJeor(input.sex, weightKg, input.heightCm, input.ageYears)

    private fun paceOf(deficitKcal: Double): Double = deficitKcal * 7.0 / ConstantsRegistry.KCAL_PER_KG_FAT

    private data class Bands(
        val fast: ForecastBand,
        val expected: ForecastBand,
        val slow: ForecastBand,
    )
}

/** Cold-start inputs (R-A5); everything a formula estimate can see. */
@Serializable
public data class ColdStartInput(
    public val sex: Sex?,
    public val ageYears: Int,
    public val heightCm: Double,
    public val startTrendKg: Double,
    public val goalWeightKg: Double,
    public val activityLevel: app.wlo.core.model.ActivityLevel,
    /** Planned daily intake for the path (the F01 draft budget). */
    public val intakeKcal: Double,
    public val startEpochDay: Long,
    /** Provenance anchor instant (the estimate's "at"); caller-supplied (D7). */
    public val startInstant: kotlinx.datetime.Instant,
)

/** Measured-mode inputs (F07 §3: measured TDEE + trailing pace distribution). */
@Serializable
public data class MeasuredInput(
    public val sex: Sex?,
    public val ageYears: Int,
    public val heightCm: Double,
    public val startTrendKg: Double,
    public val goalWeightKg: Double,
    public val intakeKcal: Double,
    public val measuredTdeeKcal: Double?,
    /** Trailing-28-day pace distribution (80th/20th percentile), kg/week. */
    public val fastPaceKgPerWeek: Double? = null,
    public val slowPaceKgPerWeek: Double? = null,
    public val typicalStepsPerDay: Int? = null,
    public val startEpochDay: Long,
    public val startInstant: kotlinx.datetime.Instant,
) {
    internal fun common(): ColdStartInput =
        ColdStartInput(
            sex = sex,
            ageYears = ageYears,
            heightCm = heightCm,
            startTrendKg = startTrendKg,
            goalWeightKg = goalWeightKg,
            activityLevel = app.wlo.core.model.ActivityLevel.SEDENTARY,
            intakeKcal = intakeKcal,
            startEpochDay = startEpochDay,
            startInstant = startInstant,
        )
}

@Serializable
public enum class ForecastMode {
    @kotlinx.serialization.SerialName("cold-start")
    COLD_START,

    @kotlinx.serialization.SerialName("measured")
    MEASURED,
}

/** One integrated band: when the goal is (or isn't) reached, and the path. */
@Serializable
public data class ForecastBand(
    /** Goal-reach day; null = not reached within the horizon (honest, not a promise). */
    public val finishEpochDay: Long?,
    /** Decaying weekly rate at each step (kg/week) — the deceleration, visible. */
    public val weeklyRatesKg: List<Double>,
    /** Projected trend weight at each step end (kg). */
    public val trajectoryKg: List<Double>,
)

/** The three bands, versioned and provenance-chipped (§2.1 provenance rule). */
@Serializable
public data class ForecastBands(
    public val mode: ForecastMode,
    public val engineVersion: String,
    public val modelVersion: String,
    public val bmrVersion: String,
    public val tdeeEstimateKcal: Double,
    public val optimistic: ForecastBand,
    public val expected: ForecastBand,
    public val pessimistic: ForecastBand,
    public val provenance: Provenance,
)
