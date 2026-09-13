package app.wlo.feature.f02.food

import app.wlo.core.engines.FoodRails
import app.wlo.core.ports.FoodSuggestion
import app.wlo.feature.f02.food.domain.NutritionLabelParser
import app.wlo.feature.f02.food.domain.ScanCorrectionLoop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The correction loop's pure half: suggestions → editable chips (rails
 * applied AFTER the analyzer, F02 §3), portion/label edits re-clamped, and
 * the OCR label parser's draft prefill.
 */
public class ScanCorrectionLoopTest {
    private fun suggestion(
        label: String,
        confidence: Double,
        kcalPer100g: Double,
        grams: Double,
    ): FoodSuggestion = FoodSuggestion(label, confidence, kcalPer100gHint = kcalPer100g, typicalGramsHint = grams)

    @Test
    public fun suggestionsBecomeEditableChipsWithPriorsAndRails() {
        val items =
            ScanCorrectionLoop.itemsFromSuggestions(
                listOf(
                    suggestion("pizza", 0.72, 266.0, 200.0),
                    suggestion("greek_salad", 0.11, 130.0, 250.0),
                ),
            )
        assertEquals(2, items.size)
        assertEquals("pizza", items[0].wireLabel)
        assertEquals("pizza", items[0].displayLabel) // display = soft name by default
        assertEquals(200.0, items[0].grams, 1e-9)
        assertEquals(266.0 * 200.0 / 100.0, items[0].kcal, 1e-9)
        assertFalse(items[0].railTripped)
    }

    @Test
    public fun densityAboveBandClampsAndFlagsTheChip() {
        // A bad "butter" prior (1200 kcal/100 g) clamps onto pure fat's 900 —
        // and the clamp surfaces as a rail marker with the kind copy.
        val chip = ScanCorrectionLoop.itemFromSuggestion(0, suggestion("butter", 0.6, 1200.0, 20.0))
        assertTrue(chip.densityClamped)
        assertEquals(900.0, chip.kcalPer100g, 1e-9)
        assertTrue(FoodRails.densityTripCopy("butter", chip.kcalPer100g).contains("pure fat"))
    }

    @Test
    public fun portionEditReclampsTheMassRail() {
        val chip = ScanCorrectionLoop.itemFromSuggestion(0, suggestion("pizza", 0.8, 266.0, 200.0))
        val inflated = ScanCorrectionLoop.withGrams(chip, 50_000.0)
        // Density-clamped per-100 values scale linearly, so the mass ceiling
        // holds without tripping: 50,000 g at 266/100 = 133,000 ≤ 50,000 × 4.
        assertFalse(inflated.railTripped)
        assertEquals(133_000.0, inflated.kcal, 1e-6)
        // The DIRECT ceiling check still clamps (the guarantee the UI leans on).
        val direct = FoodRails.clampEnergy("pizza", 300.0, 27_000_000.0)
        assertTrue(direct.tripped)
        assertEquals(1_200.0, direct.kcal, 1e-6)
    }

    @Test
    public fun labelSwapRebasesTheRail() {
        val chip = ScanCorrectionLoop.itemFromSuggestion(0, suggestion("french_fries", 0.4, 310.0, 150.0))
        val swapped =
            ScanCorrectionLoop.withLabel(
                chip,
                wireLabel = "grilled_salmon",
                displayLabel = "grilled salmon",
                kcalPer100g = 200.0,
            )
        assertEquals("grilled salmon", swapped.displayLabel)
        assertEquals(200.0 * swapped.grams / 100.0, swapped.kcal, 1e-9)
        assertFalse(swapped.railTripped)
    }

    @Test
    public fun priorAppliesTheUsersUsualCorrection() {
        val chip = ScanCorrectionLoop.itemFromSuggestion(0, suggestion("french_fries", 0.4, 310.0, 150.0))
        val applied = ScanCorrectionLoop.applyPrior(chip, priorLabel = "house salad", priorGrams = 220.0)
        assertTrue(applied.priorApplied)
        assertEquals("house salad", applied.displayLabel)
        assertEquals(220.0, applied.grams, 1e-9)
    }

    @Test
    public fun removalIsAFlagNeverADelete() {
        val chip = ScanCorrectionLoop.itemFromSuggestion(3, suggestion("hamburger", 0.6, 260.0, 220.0))
        val removed = chip.copy(removed = true)
        assertTrue(removed.removed)
        assertEquals(3, removed.id)
    }

    // --- OCR label parser (the label rung's prefill) -------------------------

    @Test
    public fun parsesAStandardLabelBlock() {
        val parsed =
            NutritionLabelParser.parse(
                """
                Nutrition Facts
                Per 100 g
                Energy 250 kcal
                Total Fat 12 g
                Carbohydrate 30 g
                Fibre 3.5 g
                Protein 8 g
                Serving size: one glass (250 ml)
                """.trimIndent(),
            )
        assertTrue(parsed.foundAny)
        assertEquals(250.0, parsed.kcalPer100g)
        assertEquals(12.0, parsed.fatGPer100g)
        assertEquals(30.0, parsed.carbGPer100g)
        assertEquals(3.5, parsed.fiberGPer100g)
        assertEquals(8.0, parsed.proteinGPer100g)
        assertEquals("Serving size: one glass (250 ml)", parsed.servingSizeText)
    }

    @Test
    public fun kJOnlyLabelsConvert() {
        val parsed = NutritionLabelParser.parse("Energy 1045 kJ\nProtein 6g")
        assertEquals(1045.0 / 4.184, parsed.kcalPer100g!!, 1e-9)
        assertEquals(6.0, parsed.proteinGPer100g)
    }

    @Test
    public fun kcalWinsOverKJWhenBothPresent() {
        val parsed = NutritionLabelParser.parse("Energy 250 kcal (1046 kJ)")
        assertEquals(250.0, parsed.kcalPer100g)
    }

    @Test
    public fun saturatedFatLineDoesNotPoisonTheFatField() {
        val parsed = NutritionLabelParser.parse("of which Saturates 2 g\nTotal Fat 9 g")
        assertEquals(9.0, parsed.fatGPer100g)
    }

    @Test
    public fun garbageTextParsesToEmptyDraft() {
        val parsed = NutritionLabelParser.parse("best before see side of pack\nlot 42X")
        assertFalse(parsed.foundAny)
        assertEquals(null, parsed.kcalPer100g)
    }
}
