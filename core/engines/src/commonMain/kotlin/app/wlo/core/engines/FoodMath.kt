package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry

/**
 * F02 diary portion math — pure, deterministic (D7). An entry's computed
 * numbers are the per-100g catalog values scaled by the portion; the formula
 * version ([ConstantsRegistry.DIARY_PORTION_FORMULA_VERSION]) rides into the
 * entry's provenance row, and the F02 §3/§8 energy-density sanity rail is
 * enforced by repositories at the food-write door.
 */
public object FoodMath {
    public const val PORTION_VERSION: String = ConstantsRegistry.DIARY_PORTION_FORMULA_VERSION

    /**
     * Sanity rail (F02 §3 "≤9 kcal/g fats", §8 "the anti-27M-kcal guarantee"):
     * per-100g energy density can never exceed pure fat. Repository policy on
     * violation: reject the catalog write — rejections are data, never clamps.
     */
    public fun withinEnergyRail(kcalPer100g: Double): Boolean = kcalPer100g >= 0.0 && kcalPer100g <= ConstantsRegistry.MAX_KCAL_PER_100G

    /** Scales one per-100 value onto a grams/ml portion; null input stays null. */
    public fun scale(
        per100: Double?,
        grams: Double,
    ): Double? = per100?.let { it * grams / 100.0 }

    /** Provenance input line for a scaled entry (goes into the inputsHash). */
    public fun scaleInputs(
        foodItemId: String?,
        grams: Double,
        kcal: Double,
    ): List<String> = listOf("food=${foodItemId ?: "none"}", "grams=$grams", "kcal=$kcal")

    /** Provenance input line for a kcal-only quick-add (F02 §4). */
    public fun quickAddInputs(
        textHint: String?,
        kcal: Double,
    ): List<String> = listOf("via=quick-add", "hint=${textHint ?: "none"}", "kcal=$kcal")
}
