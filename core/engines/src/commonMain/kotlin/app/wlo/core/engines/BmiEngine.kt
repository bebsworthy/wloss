package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Provenance

public enum class BmiCategory(
    public val label: String,
) {
    UNDERWEIGHT("underweight"),
    HEALTHY("healthy"),
    OVERWEIGHT("overweight"),
    OBESE("obese"),
}

/**
 * BMI engine — pure, deterministic, instant-in/value-out (D7: no `Clock.now()`,
 * no IO, no globals). Formula version comes from the constants registry (R-A1)
 * and is stamped into [Provenance.Derived] so the UI chip and the F07
 * Algorithms page can render exactly what produced the number.
 *
 * Precondition: [weightKg] > 0 and [heightCm] > 0, both finite. Violations are
 * programmer errors and throw [IllegalArgumentException] — they never surface
 * as user-facing errors (D8 covers data/domain failures, not contract bugs).
 */
public object BmiEngine {
    public const val FORMULA_VERSION: String = ConstantsRegistry.BMI_FORMULA_VERSION

    /** Quetelet index: kg / m². */
    public fun bmi(
        weightKg: Double,
        heightCm: Double,
    ): Double {
        require(weightKg > 0.0 && weightKg.isFinite()) { "weightKg must be positive and finite" }
        require(heightCm > 0.0 && heightCm.isFinite()) { "heightCm must be positive and finite" }
        val meters = heightCm / 100.0
        return weightKg / (meters * meters)
    }

    /** WHO adult bands, one decimal input, no gender/age adjustment in v1. */
    public fun categorize(bmi: Double): BmiCategory =
        when {
            bmi < 18.5 -> BmiCategory.UNDERWEIGHT
            bmi < 25.0 -> BmiCategory.HEALTHY
            bmi < 30.0 -> BmiCategory.OVERWEIGHT
            else -> BmiCategory.OBESE
        }

    /** The engine's only public output shape: a value that knows where it came from. */
    public fun derived(
        weightKg: Double,
        heightCm: Double,
    ): DerivedValue<Double> =
        DerivedValue(
            value = bmi(weightKg, heightCm),
            provenance =
                Provenance.Derived(
                    formulaVersion = FORMULA_VERSION,
                    inputs = listOf("weightKg", "heightCm"),
                ),
        )
}

/**
 * Girth ratios (F06 §3 derived, WLO-0043): waist÷height and waist÷hip —
 * computed, never entered; same DerivedValue shape so every ratio is
 * provenance-badged. Range copy lives with the render (§6: ranges, not
 * verdicts); the engines only do math.
 */
public object GirthRatiosEngine {
    public const val WAIST_HEIGHT_VERSION: String = ConstantsRegistry.WAIST_HEIGHT_FORMULA_VERSION
    public const val WAIST_HIP_VERSION: String = ConstantsRegistry.WAIST_HIP_FORMULA_VERSION

    /** Ashwell's simple index: waist(cm) ÷ height(cm); healthy band ≈ 0.40–0.53. */
    public fun waistToHeight(
        waistCm: Double,
        heightCm: Double,
    ): DerivedValue<Double> {
        require(waistCm > 0.0 && heightCm > 0.0) { "waist/height must be positive" }
        return DerivedValue(
            value = waistCm / heightCm,
            provenance =
                Provenance.Derived(
                    formulaVersion = WAIST_HEIGHT_VERSION,
                    inputs = listOf("waistCm", "heightCm"),
                ),
        )
    }

    /** WHO waist÷hip: risk rises above 0.90 (men) / 0.85 (women) — ranges render beside the number. */
    public fun waistToHip(
        waistCm: Double,
        hipCm: Double,
    ): DerivedValue<Double> {
        require(waistCm > 0.0 && hipCm > 0.0) { "waist/hip must be positive" }
        return DerivedValue(
            value = waistCm / hipCm,
            provenance =
                Provenance.Derived(
                    formulaVersion = WAIST_HIP_VERSION,
                    inputs = listOf("waistCm", "hipCm"),
                ),
        )
    }
}
