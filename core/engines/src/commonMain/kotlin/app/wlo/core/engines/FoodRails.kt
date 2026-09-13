package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import kotlin.math.round

/**
 * F02 sanity rails (F02 §3 "Guardrails on every estimate" + §8 "[v1] Sanity
 * rails everywhere") — PURE, model-independent, deterministic (D7): they run
 * in the interactor AFTER a capture port returns, so they are SDK-independent
 * and tested without any model. Two rails:
 *
 *  1. **Per-class energy-density clamps** — no displayed estimate may exceed
 *     the physically plausible ceiling for its food class ("≤9 kcal/g fats,
 *     ≤4 cooked starches…"). The class is matched from the label by a keyword
 *     table; UNKNOWN labels get the absolute ceiling (pure fat), never a
 *     looser guess.
 *  2. **Mass × density ceiling** — the estimated kcal can never exceed
 *     estimated grams × the class ceiling, however the estimate was derived.
 *     This is the "27M-kcal candy bar cannot render, even as a glitch"
 *     guarantee (F02 §1/§8; [FoodMath.withinEnergyRail] guards the catalog
 *     WRITE door, these rails guard every ESTIMATE the capture loop shows).
 *
 * A tripped rail CLAMPS the number (never silently: [RailOutcome.tripped] is
 * mandatory metadata that the UI renders as a sanity-rail marker + the one-
 * line explainer) and DOWNGRADES confidence (×[TRIP_CONFIDENCE_FACTOR]).
 */
public object FoodRails {
    /** Pure fat — the absolute ceiling (ConstantsRegistry, R-A1 discipline). */
    public const val MAX_KCAL_PER_GRAM: Double = ConstantsRegistry.MAX_KCAL_PER_100G / 100.0

    /** A rail trip downgrades the item's confidence by this factor (F02 §3). */
    public const val TRIP_CONFIDENCE_FACTOR: Double = 0.5

    /** How the label matched a density class (the explainer names the band). */
    public enum class DensityBand(
        public val kcalPerGram: Double,
        /** The user-facing noun for the explainer line ("denser than X"). */
        public val noun: String,
    ) {
        /** Oils, fats, butter — the physical maximum. */
        FATS(9.0, "pure fat"),

        /** Nut, seed and nut-butter dishes. */
        NUTS(7.0, "nut butter"),

        /** Sugared desserts, chocolate, ice cream. */
        SWEETS(5.0, "rich dessert"),

        /** Muscle meat and fish, incl. fatty preparations. */
        MEATS(5.0, "fatty meat"),

        /** Cheeses. */
        CHEESE(4.5, "hard cheese"),

        /** Cooked starches: bread, pasta, rice, potato dishes. */
        STARCHES(4.0, "cooked starch"),

        /** Vegetable/fruit-forward dishes, soups, salads, egg plates. */
        PRODUCE(1.5, "water-rich produce"),

        /** No keyword matched — the ABSOLUTE ceiling applies (never looser). */
        UNKNOWN(MAX_KCAL_PER_GRAM, "pure fat"),
    }

    /** Keyword → band, first match wins (lowercase contains; Food-101 labels use `_`). */
    private val KEYWORD_BANDS: List<Pair<String, DensityBand>> =
        listOf(
            // NUTS keywords precede the FATS sweep: "peanut butter" is nut
            // density, not the pure-fat ceiling (first match wins).
            "peanut" to DensityBand.NUTS,
            "hummus" to DensityBand.NUTS,
            "guacamole" to DensityBand.NUTS,
            "oil" to DensityBand.FATS,
            "butter" to DensityBand.FATS,
            "foie_gras" to DensityBand.FATS,
            "chocolate" to DensityBand.SWEETS,
            "ice_cream" to DensityBand.SWEETS,
            "frozen_yogurt" to DensityBand.SWEETS,
            "cheesecake" to DensityBand.SWEETS,
            "cup_cakes" to DensityBand.SWEETS,
            "red_velvet_cake" to DensityBand.SWEETS,
            "strawberry_shortcake" to DensityBand.SWEETS,
            "carrot_cake" to DensityBand.SWEETS,
            "cake" to DensityBand.SWEETS,
            "donuts" to DensityBand.SWEETS,
            "macarons" to DensityBand.SWEETS,
            "tiramisu" to DensityBand.SWEETS,
            "creme_brulee" to DensityBand.SWEETS,
            "panna_cotta" to DensityBand.SWEETS,
            "baklava" to DensityBand.SWEETS,
            "beignets" to DensityBand.SWEETS,
            "churros" to DensityBand.SWEETS,
            "apple_pie" to DensityBand.SWEETS,
            "bread_pudding" to DensityBand.SWEETS,
            "waffles" to DensityBand.STARCHES,
            "pancakes" to DensityBand.STARCHES,
            "french_toast" to DensityBand.STARCHES,
            "burrito" to DensityBand.STARCHES,
            "garlic_bread" to DensityBand.STARCHES,
            "bruschetta" to DensityBand.STARCHES,
            "croque_madame" to DensityBand.STARCHES,
            "sandwich" to DensityBand.STARCHES,
            "fries" to DensityBand.STARCHES,
            "nachos" to DensityBand.STARCHES,
            "poutine" to DensityBand.STARCHES,
            "onion_rings" to DensityBand.STARCHES,
            "pizza" to DensityBand.STARCHES,
            "spaghetti" to DensityBand.STARCHES,
            "pasta" to DensityBand.STARCHES,
            "lasagna" to DensityBand.STARCHES,
            "macaroni_and_cheese" to DensityBand.STARCHES,
            "ravioli" to DensityBand.STARCHES,
            "gnocchi" to DensityBand.STARCHES,
            "risotto" to DensityBand.STARCHES,
            "ramen" to DensityBand.STARCHES,
            "pho" to DensityBand.STARCHES,
            "fried_rice" to DensityBand.STARCHES,
            "pad_thai" to DensityBand.STARCHES,
            "dumplings" to DensityBand.STARCHES,
            "gyoza" to DensityBand.STARCHES,
            "samosa" to DensityBand.STARCHES,
            "takoyaki" to DensityBand.STARCHES,
            "spring_rolls" to DensityBand.STARCHES,
            "quesadilla" to DensityBand.STARCHES,
            "taco" to DensityBand.STARCHES,
            "paella" to DensityBand.STARCHES,
            "hot_dog" to DensityBand.MEATS,
            "steak" to DensityBand.MEATS,
            "prime_rib" to DensityBand.MEATS,
            "salmon" to DensityBand.MEATS,
            "tuna" to DensityBand.MEATS,
            "sashimi" to DensityBand.MEATS,
            "sushi" to DensityBand.MEATS,
            "pork" to DensityBand.MEATS,
            "pulled_pork" to DensityBand.MEATS,
            "rib" to DensityBand.MEATS,
            "chicken" to DensityBand.MEATS,
            "wings" to DensityBand.MEATS,
            "calamari" to DensityBand.MEATS,
            "scallops" to DensityBand.MEATS,
            "mussels" to DensityBand.MEATS,
            "oysters" to DensityBand.MEATS,
            "filet_mignon" to DensityBand.MEATS,
            "peking_duck" to DensityBand.MEATS,
            "beef" to DensityBand.MEATS,
            "carpaccio" to DensityBand.MEATS,
            "hamburger" to DensityBand.MEATS,
            "cheese_plate" to DensityBand.CHEESE,
            "clam_chowder" to DensityBand.PRODUCE,
            "lobster_bisque" to DensityBand.PRODUCE,
            "miso_soup" to DensityBand.PRODUCE,
            "hot_and_sour_soup" to DensityBand.PRODUCE,
            "french_onion_soup" to DensityBand.PRODUCE,
            "soup" to DensityBand.PRODUCE,
            "salad" to DensityBand.PRODUCE,
            "beet_salad" to DensityBand.PRODUCE,
            "caesar_salad" to DensityBand.PRODUCE,
            "caprese_salad" to DensityBand.PRODUCE,
            "greek_salad" to DensityBand.PRODUCE,
            "seaweed_salad" to DensityBand.PRODUCE,
            "edamame" to DensityBand.PRODUCE,
            "ceviche" to DensityBand.PRODUCE,
            "tartare" to DensityBand.PRODUCE,
            "deviled_eggs" to DensityBand.PRODUCE,
            "eggs_benedict" to DensityBand.PRODUCE,
            "omelette" to DensityBand.PRODUCE,
            "huevos_rancheros" to DensityBand.PRODUCE,
            "escargots" to DensityBand.PRODUCE,
            "falafel" to DensityBand.PRODUCE,
        )

    /**
     * The density band for a label (Food-101 wire label, display name, or any
     * free text — the match is case-insensitive, separator-tolerant).
     */
    public fun bandFor(label: String): DensityBand {
        val normalized = label.lowercase().replace('-', '_')
        for ((keyword, band) in KEYWORD_BANDS) {
            if (normalized.contains(keyword)) return band
        }
        return DensityBand.UNKNOWN
    }

    /** One clamped number with its rail metadata (the "sanity rail" marker). */
    public data class RailOutcome(
        /** The clamped kcal (≤ grams × band ceiling; unchanged when within). */
        public val kcal: Double,
        /** The grams the clamp ran against (never modified — portions are user truth). */
        public val grams: Double,
        /** True when the input exceeded the ceiling and was clamped down. */
        public val tripped: Boolean,
        /** The band that ruled (the explainer names it). */
        public val band: DensityBand,
    ) {
        /** The physical maximum for this portion (grams × band ceiling). */
        public val ceilingKcal: Double
            get() = grams * band.kcalPerGram
    }

    /** Clamps an estimated kcal onto the label's mass × density ceiling. */
    public fun clampEnergy(
        label: String,
        grams: Double,
        estimatedKcal: Double,
    ): RailOutcome {
        val band = bandFor(label)
        val ceiling = grams * band.kcalPerGram
        val clamped = if (estimatedKcal > ceiling) ceiling else estimatedKcal
        return RailOutcome(
            kcal = clamped.coerceAtLeast(0.0),
            grams = grams,
            tripped = estimatedKcal > ceiling,
            band = band,
        )
    }

    /** Downgraded confidence for a tripped rail (F02 §3 confidence downgrade). */
    public fun downgrade(confidence: Double?): Double? = confidence?.let { it * TRIP_CONFIDENCE_FACTOR }

    /** The explainer for a density-clamped hint (kind copy — facts, no blame). */
    public fun densityTripCopy(
        label: String,
        clampedKcalPer100g: Double,
    ): String =
        "That'd be denser than ${bandFor(label).noun} — ${formatNumber(clampedKcalPer100g)} kcal per 100 g " +
            "is the physical ceiling. Check the label once more; the number stays capped there until then."

    /** The one-line explainer for a tripped rail (kind copy — facts, no blame). */
    public fun tripCopy(outcome: RailOutcome): String =
        "That'd be denser than ${outcome.band.noun} — ${formatNumber(outcome.ceilingKcal)} kcal is the " +
            "physical ceiling for ${formatNumber(outcome.grams)} g. Check the portion or the label; " +
            "the number stays capped there until then."

    /**
     * Clamps a per-100 g density estimate onto its band ceiling (rail 1).
     * Rail 2 ([clampEnergy]) is then automatically satisfied by any portion
     * scaled from this density — the two rails compose: density-clamped
     * per-100 values can never push a portion past the mass ceiling.
     */
    public fun clampDensity(
        label: String,
        kcalPer100g: Double,
    ): Double {
        val ceiling = bandFor(label).kcalPerGram * 100.0
        return if (kcalPer100g > ceiling) ceiling else kcalPer100g
    }

    /** Per-100 g rail check for a scanned estimate's density hint (belt to FoodMath's braces). */
    public fun withinBandDensity(
        label: String,
        kcalPer100g: Double,
    ): Boolean = kcalPer100g <= bandFor(label).kcalPerGram * 100.0

    /** Common-main-safe number format (no java.text): integers when whole, else 1 decimal. */
    internal fun formatNumber(value: Double): String {
        val rounded = round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
