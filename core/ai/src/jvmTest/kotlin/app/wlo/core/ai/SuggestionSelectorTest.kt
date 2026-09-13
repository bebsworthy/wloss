package app.wlo.core.ai

import app.wlo.core.ports.PhotoAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The model-independent half of the analyzer: softmax math, top-k mapping,
 * the hold threshold, and the pinned 101-class catalog (ADR-007: class list
 * fixed at adoption, training order = codepoint sort).
 */
public class SuggestionSelectorTest {
    @Test
    public fun catalogIsPinnedTo101SortedLabels() {
        assertTrue(Food101Catalog.verify(), "101 labels, codepoint-sorted (training order)")
        assertEquals("apple_pie", Food101Catalog.labelAt(0))
        assertEquals("waffles", Food101Catalog.labelAt(100))
        // The codepoint-vs-locale trap: cheese_plate sorts BEFORE cheesecake.
        assertEquals("cheese_plate", Food101Catalog.labelAt(16))
        assertEquals("cheesecake", Food101Catalog.labelAt(17))
    }

    @Test
    public fun everyLabelCarriesACoarsePrior() {
        for (label in Food101Catalog.LABELS) {
            val prior = Food101Catalog.priorFor(label)
            assertTrue(prior != null, "prior missing for $label")
            prior!!
            assertTrue(prior.kcalPer100g in 1.0..900.0, "kcal prior out of range for $label")
            assertTrue(prior.typicalGrams in 10.0..1000.0, "grams prior out of range for $label")
        }
    }

    @Test
    public fun softmaxSumsToOneAndRespectsOrder() {
        val probabilities = SuggestionSelector.softmax(floatArrayOf(0.1f, 2.0f, -1.0f, 0.5f))
        assertEquals(4, probabilities.size)
        assertEquals(1.0, probabilities.sum(), 1e-9)
        assertTrue(probabilities[1] > probabilities[3])
        assertTrue(probabilities[3] > probabilities[0])
        assertTrue(probabilities[0] > probabilities[2])
    }

    @Test
    public fun softmaxIsStableForHugeLogits() {
        val probabilities = SuggestionSelector.softmax(floatArrayOf(1000f, 999f, 500f))
        assertEquals(1.0, probabilities.sum(), 1e-9)
        assertTrue(probabilities[0] > 0.5)
    }

    @Test
    public fun topKSelectsHighestAndCarriesPriors() {
        val logits = FloatArray(Food101Catalog.EXPECTED_CLASSES)
        logits[Food101Catalog.LABELS.indexOf("pizza")] = 6f
        logits[Food101Catalog.LABELS.indexOf("hamburger")] = 4f
        logits[Food101Catalog.LABELS.indexOf("french_fries")] = 2f

        val (suggestions, held) = SuggestionSelector.select(logits)
        assertFalse(held, "a dominant top-1 is not held at the 0.35 threshold")
        assertEquals(PhotoAnalyzer.TOP_K, suggestions.size)
        assertEquals("pizza", suggestions[0].label)
        assertEquals("hamburger", suggestions[1].label)
        assertEquals("french_fries", suggestions[2].label)
        assertTrue(suggestions[0].confidence > suggestions[1].confidence)
        // Priors ride the chips (they become the editable estimates).
        assertEquals(Food101Catalog.priorFor("pizza")!!.kcalPer100g, suggestions[0].kcalPer100gHint)
        assertEquals(Food101Catalog.priorFor("pizza")!!.typicalGrams, suggestions[0].typicalGramsHint)
    }

    @Test
    public fun lowTop1ConfidenceHoldsTheScan() {
        val logits = FloatArray(Food101Catalog.EXPECTED_CLASSES)
        // Near-uniform logits → top-1 well below the threshold.
        for (i in logits.indices) logits[i] = 0.001f * i
        val (suggestions, held) = SuggestionSelector.select(logits, holdThreshold = PhotoAnalyzer.DEFAULT_HOLD_CONFIDENCE)
        assertTrue(held, "a diffuse scan must hold (never present guesses as facts)")
        assertEquals(PhotoAnalyzer.TOP_K, suggestions.size)
    }

    @Test
    public fun thresholdIsTheFlaggedDefaultAndAboveItHolds() {
        assertEquals(0.35, PhotoAnalyzer.DEFAULT_HOLD_CONFIDENCE, "OWNER FLAG default — changing it is a decision")
        val logits = FloatArray(Food101Catalog.EXPECTED_CLASSES)
        logits[10] = 1.0f // softmax over near-uniform → top1 ≈ 1/101 ≈ 0.0099 < 0.35 → held
        val (_, held) = SuggestionSelector.select(logits)
        assertTrue(held)
    }
}
