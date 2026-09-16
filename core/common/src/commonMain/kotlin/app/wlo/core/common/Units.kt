package app.wlo.core.common

import app.wlo.core.model.MeasureUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Unit utilities — metric default, imperial a user setting (R-D10). Rendering
 * is always settings-driven; no unit is ever hardcoded in copy.
 */
public enum class MassUnit(
    public val symbol: String,
    private val kgPerUnit: Double,
) {
    /** Default and fallback (R-D10). */
    KILOGRAM("kg", 1.0),
    POUND("lb", 0.45359237),
    ;

    public fun fromKilograms(kg: Double): Double = kg / kgPerUnit

    public fun toKilograms(value: Double): Double = value * kgPerUnit

    /** Numeral-only display value, rounded at the unit boundary. */
    public fun formatNumber(kg: Double): String {
        val tenths = (fromKilograms(kg) * 10).roundToLong()
        val magnitude = abs(tenths)
        val sign = if (tenths < 0) "-" else ""
        return "$sign${magnitude / 10}.${magnitude % 10}"
    }

    /** Display string like "81.2 kg" (one decimal, tabular-figure friendly). */
    public fun format(kg: Double): String = "${formatNumber(kg)} $symbol"

    public companion object {
        public val DEFAULT: MassUnit = KILOGRAM
    }
}

/** Length units for height; metric default (R-D10). */
public enum class LengthUnit(
    public val symbol: String,
    private val cmPerUnit: Double,
) {
    CENTIMETER("cm", 1.0),
    INCH("in", 2.54),
    ;

    public fun fromCentimeters(cm: Double): Double = cm / cmPerUnit

    public fun toCentimeters(value: Double): Double = value * cmPerUnit

    public companion object {
        public val DEFAULT: LengthUnit = CENTIMETER
    }
}

// --- Kitchen measures (F03↔F04 canonical item space; units live in :core:model,
//     conversion math lives here — the R-D10 converter is the single owner). ---

/** Converts a quantity into its dimension's canonical amount (g / ml / pieces). */
public fun MeasureUnit.toCanonical(value: Double): Double = value * canonicalFactor

/** Reads a canonical amount (g / ml / pieces) back into this unit. */
public fun MeasureUnit.fromCanonical(canonical: Double): Double = canonical / canonicalFactor

/**
 * Same-dimension unit conversion ("1 cup" → "240 ml"). Cross-dimension
 * requests return null BY CONTRACT (F04 §3: incompatible units never fake a
 * conversion — callers group per-source amounts instead of converting).
 * Grocery density hints may bridge mass/volume where the item allows it, but
 * that decision belongs to the caller, never to this function.
 */
public fun convertMeasure(
    value: Double,
    from: MeasureUnit,
    to: MeasureUnit,
): Double? =
    if (from.kind != to.kind) {
        null
    } else {
        from.fromCanonical(from.toCanonical(value))
    }
