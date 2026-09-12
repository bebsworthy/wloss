package app.wlo.core.common

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

    /** Display string like "81.2 kg" (one decimal, tabular-figure friendly). */
    public fun format(kg: Double): String {
        val converted = fromKilograms(kg)
        val tenths = (converted * 10).roundToLong()
        val whole = tenths / 10
        val fraction = tenths % 10
        return "$whole.$fraction $symbol"
    }

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
