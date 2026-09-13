package app.wlo.core.ai

import app.wlo.core.model.Analysis
import app.wlo.core.model.Provenance
import app.wlo.core.ports.FoodSuggestion
import app.wlo.core.ports.PhotoAnalyzer
import kotlinx.datetime.Instant
import kotlin.math.exp

/**
 * Pure top-k / threshold mapping over classifier logits — the HALF of the
 * analyzer that is unit-testable without ONNX (the androidMain [OnnxPhotoAnalyzer]
 * owns the session + preprocessing; this object owns the math). Deterministic
 * (D7): logits in, suggestions + held flag out.
 */
public object SuggestionSelector {
    /**
     * Numerically-stable softmax over the raw logits → probabilities in [0, 1]
     * summing (up to fp error) to 1.
     */
    public fun softmax(logits: FloatArray): DoubleArray {
        require(logits.isNotEmpty()) { "softmax of empty logits" }
        var max = logits[0]
        for (v in logits) if (v > max) max = v
        val exps = DoubleArray(logits.size)
        var sum = 0.0
        for (i in logits.indices) {
            exps[i] = exp((logits[i] - max).toDouble())
            sum += exps[i]
        }
        for (i in exps.indices) exps[i] /= sum
        return exps
    }

    /**
     * Selects the top-[topK] suggestions by probability, attaching the class
     * priors from [Food101Catalog]; the returned `held` flag is true when the
     * TOP-1 probability is below [holdThreshold] (the whole scan is then a
     * "rough guess" — R-U15: editable before save, never auto-committed).
     */
    public fun select(
        logits: FloatArray,
        topK: Int = PhotoAnalyzer.TOP_K,
        holdThreshold: Double = PhotoAnalyzer.DEFAULT_HOLD_CONFIDENCE,
    ): Pair<List<FoodSuggestion>, Boolean> {
        val probabilities = softmax(logits)
        val order =
            probabilities
                .withIndex()
                .sortedByDescending { it.value }
                .take(topK.coerceAtMost(probabilities.size))
        val suggestions =
            order.map { (index, probability) ->
                val label = Food101Catalog.labelAt(index) ?: "unknown_class_$index"
                val prior = Food101Catalog.priorFor(label)
                FoodSuggestion(
                    label = label,
                    confidence = probability,
                    kcalPer100gHint = prior?.kcalPer100g,
                    typicalGramsHint = prior?.typicalGrams,
                )
            }
        val top1 = probabilities.max()
        return suggestions to (top1 < holdThreshold)
    }

    /**
     * The uniform [Analysis] shape for a selection: on-device provenance
     * (local inference needs no consent — `consentGranted = false` is the
     * honest "no cloud was involved", not a denial). [at] is the analysis
     * instant (injected — no clock reads in shared code).
     */
    public fun toAnalysis(
        suggestions: List<FoodSuggestion>,
        held: Boolean,
        modelId: String,
        at: Instant,
    ): Analysis<List<FoodSuggestion>> =
        Analysis(
            value = suggestions,
            confidence = suggestions.firstOrNull()?.confidence,
            provenance =
                Provenance.Estimated(
                    at = at,
                    method = "on-device classifier",
                    confidence = suggestions.firstOrNull()?.confidence,
                    modelId = modelId,
                    consentGranted = false,
                ),
            held = held,
        )
}
