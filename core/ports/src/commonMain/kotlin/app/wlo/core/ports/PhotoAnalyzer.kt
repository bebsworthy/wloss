package app.wlo.core.ports

import app.wlo.core.model.Analysis

/**
 * Food photo recognition — capture ladder first step (F02 §3 rung 1); cloud
 * assist is gated by the `food-photo` capability (F12 §3.1). The on-device
 * analyzer NEVER needs consent and NEVER needs network (R-S14 zoo model);
 * with the model absent it returns a held, empty analysis — never a crash,
 * never a guess — and the UI offers the model zoo (R-U15's manual twin is
 * always visible alongside).
 */
public interface PhotoAnalyzer {
    /**
     * Analyzes one in-memory frame. The result carries the top-[TOP_K] class
     * suggestions with confidences; the Analysis-level `held` flag is set when
     * the top-1 confidence is below [DEFAULT_HOLD_CONFIDENCE] (or the model
     * could not run at all).
     */
    public suspend fun analyze(frame: CapturedFrame): Analysis<List<FoodSuggestion>>

    public companion object {
        /** Suggestions offered per scan (editable chips in the F02 loop). */
        public const val TOP_K: Int = 5

        /**
         * OWNER FLAG — default low-confidence hold threshold (M4). Below this
         * top-1 confidence the scan ships `held = true` and the UI says
         * "rough guess" instead of presenting estimates as facts (F02 §4's
         * amber strip sits at 0.5 for the scan strip; this is the analyzer's
         * stricter bar). Re-tune against real-world captures before v1.
         */
        public const val DEFAULT_HOLD_CONFIDENCE: Double = 0.35
    }
}

/**
 * One recognized food class (Food-101 label for model v1) with its softmax
 * confidence and the class PRIORS that turn a class into an estimate: a
 * coarse per-100 g energy-density hint and a typical-portion hint. Both hints
 * are explicitly estimates — the sanity rails (:core:engines FoodRails) clamp
 * whatever they produce, and the correction loop lets the user replace them
 * before save (nothing auto-commits, F02 §1).
 */
public data class FoodSuggestion(
    /** Model-native class label (Food-101 wire name, e.g. `french_fries`). */
    public val label: String,
    /** Softmax confidence in [0, 1]. */
    public val confidence: Double,
    /** Coarse per-100 g energy-density prior for the class (kcal); null when unknown. */
    public val kcalPer100gHint: Double? = null,
    /** Typical single-serving mass prior for the class (grams); null when unknown. */
    public val typicalGramsHint: Double? = null,
)
