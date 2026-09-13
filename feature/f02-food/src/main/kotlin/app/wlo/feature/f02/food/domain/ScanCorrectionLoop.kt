package app.wlo.feature.f02.food.domain

import app.wlo.core.engines.FoodRails
import app.wlo.core.model.Analysis
import app.wlo.core.model.Provenance
import app.wlo.core.ports.FoodSuggestion
import kotlin.math.round

/**
 * The scan → editable-items half of the correction loop (F02 §3/§4): a pure,
 * model-independent transform from the analyzer's suggestions to the chips
 * the user edits before save. The sanity rails (:core:engines FoodRails) run
 * HERE — after the port returns, before anything renders — so "no displayed
 * estimate can exceed physically plausible bounds for its food class" holds
 * for every pixel of UI (F02 §1) and the rails stay testable without a model.
 */
public object ScanCorrectionLoop {
    /** One editable chip: the model's guess plus the user's (default = prior's) numbers. */
    public data class ScanItem(
        /** Stable id for list keys (index in the suggestion list). */
        public val id: Int,
        /** The model's wire label (Food-101), e.g. `french_fries`. */
        public val wireLabel: String,
        /** The label the chip shows (a correction-cache prior may already replace it). */
        public val displayLabel: String,
        /** Model confidence for THIS class (0..1). */
        public val confidence: Double,
        /** Portion estimate in grams (class prior, or the user's correction-cache value). */
        public val grams: Double,
        /** Per-100 g energy estimate (class prior, post-rail). */
        public val kcalPer100g: Double,
        /** Rail outcome for the CURRENT grams × density (re-run on edits). */
        public val rail: FoodRails.RailOutcome,
        /** True when this item's rail tripped (held + copy, F02 §3). */
        public val railTripped: Boolean = rail.tripped,
        /** True when the class's density hint was clamped onto the band ceiling. */
        public val densityClamped: Boolean = false,
        /** True when a correction-cache prior replaced the model's label. */
        public val priorApplied: Boolean = false,
        /** The catalog food this chip is resolved to (assigned at save/search-pick). */
        public val resolvedFoodId: String? = null,
        /** True when the user removed the chip ("not this"). */
        public val removed: Boolean = false,
    ) {
        /** The clamped, current kcal for the chip (grams × per-100 g, rail-checked). */
        public val kcal: Double
            get() = rail.kcal
    }

    /**
     * Suggestions → chips: portion = the class prior's typical grams, energy =
     * per-100 g prior scaled, THEN the rails clamp + downgrade. Held scans
     * (top-1 below threshold) keep their chips — the UI shows the amber
     * strip; nothing here guesses beyond the model's own priors.
     */
    public fun itemsFromSuggestions(suggestions: List<FoodSuggestion>): List<ScanItem> =
        suggestions.mapIndexed { index, suggestion -> itemFromSuggestion(index, suggestion) }

    public fun itemFromSuggestion(
        id: Int,
        suggestion: FoodSuggestion,
    ): ScanItem {
        // Rail 1 first: the density hint itself is clamped to the class band,
        // so every portion scaled from it is pre-clamped for rail 2. A clamp
        // IS a rail hit: the chip gets the marker + the explainer.
        val rawPer100 = suggestion.kcalPer100gHint ?: DEFAULT_KCAL_PER_100G
        val per100 = FoodRails.clampDensity(suggestion.label, rawPer100)
        val grams = suggestion.typicalGramsHint ?: DEFAULT_GRAMS
        return ScanItem(
            id = id,
            wireLabel = suggestion.label,
            displayLabel = displayName(suggestion.label),
            confidence = suggestion.confidence,
            grams = grams,
            kcalPer100g = per100,
            rail = FoodRails.clampEnergy(suggestion.label, grams, per100 * grams / 100.0),
            densityClamped = per100 < rawPer100,
        )
    }

    /** Re-runs the rail after a portion edit (the interactor's per-keystroke clamp). */
    public fun withGrams(
        item: ScanItem,
        grams: Double,
    ): ScanItem {
        val safe = grams.coerceAtLeast(0.0)
        return item.copy(
            grams = safe,
            rail = FoodRails.clampEnergy(item.wireLabel, safe, item.kcalPer100g * safe / 100.0),
        )
    }

    /** A label swap ("this isn't fries, it's grilled salmon"): re-pins the rail. */
    public fun withLabel(
        item: ScanItem,
        wireLabel: String,
        displayLabel: String,
        kcalPer100g: Double?,
    ): ScanItem =
        item
            .copy(
                wireLabel = wireLabel,
                displayLabel = displayLabel,
                kcalPer100g = kcalPer100g?.let { FoodRails.clampDensity(wireLabel, it) } ?: item.kcalPer100g,
            ).let { relabeled ->
                relabeled.copy(
                    rail =
                        FoodRails.clampEnergy(
                            relabeled.wireLabel,
                            relabeled.grams,
                            relabeled.kcalPer100g * relabeled.grams / 100.0,
                        ),
                )
            }

    /**
     * Applies a correction-cache prior: the user's usual swap replaces the
     * model label (R-B6 — "corrections persist as ground truth and visibly
     * improve future estimates").
     */
    public fun applyPrior(
        item: ScanItem,
        priorLabel: String,
        priorGrams: Double?,
    ): ScanItem =
        withGrams(
            withLabel(item, priorLabel, priorLabel, item.kcalPer100g),
            priorGrams ?: item.grams,
        ).copy(priorApplied = true)

    /** The Analysis-level provenance line for the result card (visible, D6). */
    public fun scanProvenance(analysis: Analysis<List<FoodSuggestion>>): Provenance = analysis.provenance

    /** Soft display name: `french_fries` → "french fries". */
    public fun displayName(wireLabel: String): String = wireLabel.replace('_', ' ')

    /** Chip-side quantization: grams shown/edited in whole grams (portion detents are v1.x). */
    public fun roundGrams(grams: Double): Double = round(grams).coerceAtLeast(0.0)

    public const val DEFAULT_KCAL_PER_100G: Double = 200.0

    public const val DEFAULT_GRAMS: Double = 150.0
}
