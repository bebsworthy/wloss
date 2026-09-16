package app.wlo.core.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.min

/** Goal direction selected explicitly by the user (WLO-0068). */
@Serializable
public enum class WeightGoalMode {
    LOSS,
    MAINTENANCE,
    GAIN,
}

/**
 * A non-diagnostic answer to a safety-screening question. `NOT_ANSWERED`
 * holds goal math; it never gets interpreted as `NO`.
 */
@Serializable
public enum class SafetyAnswer {
    NO,
    YES,
    NOT_ANSWERED,
}

/**
 * All inputs needed by the shared weight-goal product-safety boundary.
 *
 * This is not a medical assessment. The answers only decide whether WLO's
 * generic, automated goal and date math is within its documented support
 * envelope. Raw weight tracking remains available for every result.
 */
@Serializable
public data class WeightGoalSafetyInput(
    public val ageYears: Int?,
    /** Null means tracking-only: no target or forecast was requested. */
    public val mode: WeightGoalMode?,
    public val currentWeightKg: Double?,
    public val targetWeightKg: Double? = null,
    /** Non-negative magnitude; [mode] owns direction. */
    public val requestedPacePctPerWeek: Double? = null,
    /** Optional plan budget. Weight-only tracking does not require one. */
    public val plannedDailyEnergyKcal: Double? = null,
    /** Configured product floor. This contract does not invent a medical floor. */
    public val minimumDailyEnergyKcal: Double? = null,
    public val pregnant: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val breastfeeding: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val eatingDisorderConcern: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val medicallyInfluencedWeight: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
)

/**
 * One result consumed by goal UI and forecast engines. Consumers must check
 * [allowsGoalMath] rather than rebuilding safety rules locally.
 */
public sealed interface WeightGoalEligibility {
    public val allowsGoalMath: Boolean

    /** No goal was requested. Tracking is fully supported; no date exists. */
    public data object TrackingOnly : WeightGoalEligibility {
        override val allowsGoalMath: Boolean = false
    }

    /** Goal and forecast math may run inside the returned support envelope. */
    public data class Eligible(
        public val mode: WeightGoalMode,
        public val requestedPacePctPerWeek: Double,
        public val maximumPacePctPerWeek: Double,
    ) : WeightGoalEligibility {
        override val allowsGoalMath: Boolean = true
    }

    /** Inputs are valid, but generic targets/dates must be withheld. */
    public data class Held(
        public val mode: WeightGoalMode?,
        public val reasons: Set<WeightGoalHoldReason>,
        public val maximumPacePctPerWeek: Double? = null,
    ) : WeightGoalEligibility {
        override val allowsGoalMath: Boolean = false
    }

    /** WLO's adult goal engine does not support this person or input shape. */
    public data class Unsupported(
        public val reason: WeightGoalUnsupportedReason,
    ) : WeightGoalEligibility {
        override val allowsGoalMath: Boolean = false
    }
}

public enum class WeightGoalHoldReason {
    CURRENT_WEIGHT_REQUIRED,
    SCREENING_INCOMPLETE,
    PREGNANCY,
    BREASTFEEDING,
    EATING_DISORDER_CONCERN,
    MEDICALLY_INFLUENCED_WEIGHT,
    MODE_TARGET_MISMATCH,
    PACE_REQUIRED,
    PACE_OUTSIDE_SUPPORTED_RANGE,
    BELOW_CONFIGURED_ENERGY_FLOOR,
    GAIN_FORECAST_UNAVAILABLE,
}

public enum class WeightGoalUnsupportedReason {
    MINOR,
    INVALID_AGE,
    INVALID_CURRENT_WEIGHT,
    INVALID_TARGET_WEIGHT,
    INVALID_PACE,
    INVALID_ENERGY_VALUES,
}

/** Pure, deterministic WLO product-policy gate (WLO-0080). */
public object WeightGoalSafety {
    public fun evaluate(input: WeightGoalSafetyInput): WeightGoalEligibility {
        val invalid = validateShape(input)
        if (invalid != null) return WeightGoalEligibility.Unsupported(invalid)

        // Safety screening gates automated goal math, never raw tracking.
        val mode = input.mode ?: return WeightGoalEligibility.TrackingOnly

        val age = input.ageYears
        if (age == null) {
            // Keep evaluating so the caller receives every held dependency,
            // including a missing current weight, in one deterministic result.
        } else if (age < ConstantsRegistry.WEIGHT_GOAL_MIN_AGE_YEARS) {
            return WeightGoalEligibility.Unsupported(WeightGoalUnsupportedReason.MINOR)
        }

        val reasons = linkedSetOf<WeightGoalHoldReason>()
        if (age == null) reasons += WeightGoalHoldReason.SCREENING_INCOMPLETE
        collectScreeningReasons(input, reasons)
        collectGoalReasons(input, mode, reasons)
        collectEnergyReasons(input, reasons)

        val currentWeight = input.currentWeightKg
        if (currentWeight == null) reasons += WeightGoalHoldReason.CURRENT_WEIGHT_REQUIRED
        val maximumPace = currentWeight?.let { maximumPacePctPerWeek(mode, it) }
        val requestedPace = input.requestedPacePctPerWeek ?: if (mode == WeightGoalMode.MAINTENANCE) 0.0 else null
        if (requestedPace == null || (mode != WeightGoalMode.MAINTENANCE && requestedPace <= EPSILON)) {
            reasons += WeightGoalHoldReason.PACE_REQUIRED
        } else if (maximumPace != null && requestedPace > maximumPace + EPSILON) {
            reasons += WeightGoalHoldReason.PACE_OUTSIDE_SUPPORTED_RANGE
        }

        return if (reasons.isEmpty()) {
            WeightGoalEligibility.Eligible(
                mode = mode,
                requestedPacePctPerWeek = requestedPace ?: 0.0,
                maximumPacePctPerWeek = checkNotNull(maximumPace),
            )
        } else {
            WeightGoalEligibility.Held(mode = mode, reasons = reasons, maximumPacePctPerWeek = maximumPace)
        }
    }

    public fun maximumPacePctPerWeek(
        mode: WeightGoalMode,
        currentWeightKg: Double,
    ): Double =
        when (mode) {
            WeightGoalMode.LOSS ->
                min(
                    ConstantsRegistry.PACE_CAP_PCT_PER_WEEK,
                    ConstantsRegistry.WEIGHT_GOAL_MAX_LOSS_KG_PER_WEEK / currentWeightKg * 100.0,
                )
            WeightGoalMode.MAINTENANCE -> 0.0
            WeightGoalMode.GAIN -> ConstantsRegistry.WEIGHT_GOAL_MAX_GAIN_PCT_PER_WEEK
        }

    private fun validateShape(input: WeightGoalSafetyInput): WeightGoalUnsupportedReason? {
        val age = input.ageYears
        if (age != null && age !in 0..MAX_PLAUSIBLE_AGE) return WeightGoalUnsupportedReason.INVALID_AGE
        if (input.currentWeightKg != null && (!input.currentWeightKg.isFinite() || input.currentWeightKg <= 0.0)) {
            return WeightGoalUnsupportedReason.INVALID_CURRENT_WEIGHT
        }
        val target = input.targetWeightKg
        if (target != null && (!target.isFinite() || target <= 0.0)) {
            return WeightGoalUnsupportedReason.INVALID_TARGET_WEIGHT
        }
        val pace = input.requestedPacePctPerWeek
        if (pace != null && (!pace.isFinite() || pace < 0.0)) {
            return WeightGoalUnsupportedReason.INVALID_PACE
        }
        val energyValues = listOfNotNull(input.plannedDailyEnergyKcal, input.minimumDailyEnergyKcal)
        if (energyValues.any { !it.isFinite() || it <= 0.0 }) {
            return WeightGoalUnsupportedReason.INVALID_ENERGY_VALUES
        }
        return null
    }

    private fun collectScreeningReasons(
        input: WeightGoalSafetyInput,
        reasons: MutableSet<WeightGoalHoldReason>,
    ) {
        val screening =
            listOf(
                input.pregnant to WeightGoalHoldReason.PREGNANCY,
                input.breastfeeding to WeightGoalHoldReason.BREASTFEEDING,
                input.eatingDisorderConcern to WeightGoalHoldReason.EATING_DISORDER_CONCERN,
                input.medicallyInfluencedWeight to WeightGoalHoldReason.MEDICALLY_INFLUENCED_WEIGHT,
            )
        if (screening.any { (answer, _) -> answer == SafetyAnswer.NOT_ANSWERED }) {
            reasons += WeightGoalHoldReason.SCREENING_INCOMPLETE
        }
        screening.filter { (answer, _) -> answer == SafetyAnswer.YES }.forEach { (_, reason) -> reasons += reason }
    }

    private fun collectGoalReasons(
        input: WeightGoalSafetyInput,
        mode: WeightGoalMode,
        reasons: MutableSet<WeightGoalHoldReason>,
    ) {
        val target = input.targetWeightKg
        val current = input.currentWeightKg ?: return
        val matches =
            when (mode) {
                WeightGoalMode.LOSS -> target != null && target < current
                WeightGoalMode.GAIN -> target != null && target > current
                WeightGoalMode.MAINTENANCE ->
                    target == null ||
                        abs(target - current) / current * 100.0 <=
                        ConstantsRegistry.WEIGHT_GOAL_MAINTENANCE_BAND_PCT
            }
        if (!matches) reasons += WeightGoalHoldReason.MODE_TARGET_MISMATCH
    }

    private fun collectEnergyReasons(
        input: WeightGoalSafetyInput,
        reasons: MutableSet<WeightGoalHoldReason>,
    ) {
        val planned = input.plannedDailyEnergyKcal
        val floor = input.minimumDailyEnergyKcal
        if (planned != null && floor != null && planned < floor) {
            reasons += WeightGoalHoldReason.BELOW_CONFIGURED_ENERGY_FLOOR
        }
    }

    private const val EPSILON: Double = 1e-9
    private const val MAX_PLAUSIBLE_AGE: Int = 130
}

/** Neutral accessible copy; it explains WLO policy and never diagnoses. */
public data class WeightGoalSafetyCopy(
    public val title: String,
    public val body: String,
)

public object WeightGoalSafetyCopyPolicy {
    public fun forResult(result: WeightGoalEligibility): WeightGoalSafetyCopy =
        when (result) {
            WeightGoalEligibility.TrackingOnly ->
                WeightGoalSafetyCopy("Tracking only", "You can record weight without setting a target or date.")
            is WeightGoalEligibility.Eligible ->
                WeightGoalSafetyCopy("Goal supported", "The goal is inside WLO's product support range.")
            is WeightGoalEligibility.Unsupported -> unsupportedCopy(result.reason)
            is WeightGoalEligibility.Held -> heldCopy(result)
        }

    private fun unsupportedCopy(reason: WeightGoalUnsupportedReason): WeightGoalSafetyCopy =
        when (reason) {
            WeightGoalUnsupportedReason.MINOR ->
                WeightGoalSafetyCopy(
                    "Adult goals only",
                    "WLO's automated goals and forecasts are designed for adults. Weight tracking remains available; a qualified healthcare professional can help with an age-appropriate plan.",
                )
            else ->
                WeightGoalSafetyCopy(
                    "Check the goal details",
                    "WLO cannot calculate a goal or date from these values. Your weight record is unchanged.",
                )
        }

    private fun heldCopy(result: WeightGoalEligibility.Held): WeightGoalSafetyCopy {
        val primary = result.reasons.first()
        return when (primary) {
            WeightGoalHoldReason.CURRENT_WEIGHT_REQUIRED ->
                WeightGoalSafetyCopy("First weight needed", "Add a current weight before WLO evaluates this goal or calculates a date.")
            WeightGoalHoldReason.SCREENING_INCOMPLETE ->
                WeightGoalSafetyCopy("A few answers needed", "Answer the safety questions before WLO creates a goal or forecast date.")
            WeightGoalHoldReason.PREGNANCY ->
                WeightGoalSafetyCopy(
                    "Use pregnancy-specific guidance",
                    "Generic goal and date math is paused during pregnancy. Weight tracking remains available; use a plan agreed with your maternity care team.",
                )
            WeightGoalHoldReason.BREASTFEEDING ->
                WeightGoalSafetyCopy(
                    "Use breastfeeding-specific guidance",
                    "Generic goal and date math is paused while breastfeeding because energy needs vary. Weight tracking remains available; consider guidance from a qualified professional.",
                )
            WeightGoalHoldReason.EATING_DISORDER_CONCERN ->
                WeightGoalSafetyCopy(
                    "Goals and dates paused",
                    "WLO will not turn this concern into a target or countdown. Weight tracking remains available; consider support from a qualified professional.",
                )
            WeightGoalHoldReason.MEDICALLY_INFLUENCED_WEIGHT ->
                WeightGoalSafetyCopy(
                    "A tailored plan may help",
                    "Medication or a health condition can affect weight, so WLO is holding generic targets and dates. Weight tracking remains available.",
                )
            WeightGoalHoldReason.PACE_OUTSIDE_SUPPORTED_RANGE ->
                WeightGoalSafetyCopy(
                    "Choose a slower pace",
                    "That pace is outside WLO's supported product range. Nothing was saved or silently changed.",
                )
            WeightGoalHoldReason.BELOW_CONFIGURED_ENERGY_FLOOR ->
                WeightGoalSafetyCopy(
                    "Plan held at the energy floor",
                    "This plan falls below your configured floor. WLO will not save it or produce a forecast date.",
                )
            WeightGoalHoldReason.GAIN_FORECAST_UNAVAILABLE ->
                WeightGoalSafetyCopy(
                    "Gain date held",
                    "The gain goal is saved, but WLO's current forecast model is loss-only and will not invent a gain date.",
                )
            WeightGoalHoldReason.MODE_TARGET_MISMATCH,
            WeightGoalHoldReason.PACE_REQUIRED,
            ->
                WeightGoalSafetyCopy(
                    "Check the goal details",
                    "The goal mode, target, and pace need to agree before WLO calculates a date.",
                )
        }
    }
}
