package app.wlo.core.ports

import app.wlo.core.model.Analysis

/**
 * Food photo recognition — capture ladder first step (F02); cloud assist is
 * gated by the `food-photo` capability (F12 §3.1).
 */
public interface PhotoAnalyzer {
    public suspend fun analyze(frame: CapturedFrame): Analysis<FoodEstimate>
}

/** Recognized food item with an energy estimate (kcal). */
public data class FoodEstimate(
    public val label: String,
    public val kcal: Double,
    public val grams: Double?,
)
