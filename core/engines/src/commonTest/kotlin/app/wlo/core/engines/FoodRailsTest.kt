package app.wlo.core.engines

import app.wlo.core.engines.FoodRails.DensityBand
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The F02 sanity rails, tested WITHOUT any model (ARCHITECTURE §2.4: rails
 * run after the port returns, so they are SDK-independent and table-driven
 * + property-tested here). The anti-27M-kcal guarantee lives in these cases.
 */
public class FoodRailsTest {
    // --- the keyword table ---------------------------------------------------

    @Test
    public fun knownClassesMapToTheirDensityBands() {
        val expected =
            mapOf(
                "butter" to DensityBand.FATS,
                "olive_oil_drizzle" to DensityBand.FATS,
                "peanut_butter" to DensityBand.NUTS,
                "hummus" to DensityBand.NUTS,
                "chocolate_cake" to DensityBand.SWEETS,
                "ice_cream" to DensityBand.SWEETS,
                "grilled_salmon" to DensityBand.MEATS,
                "hamburger" to DensityBand.MEATS,
                "parmesan_cheese_plate" to DensityBand.CHEESE,
                "pizza" to DensityBand.STARCHES,
                "french_fries" to DensityBand.STARCHES,
                "spaghetti_carbonara" to DensityBand.STARCHES,
                "greek_salad" to DensityBand.PRODUCE,
                "miso_soup" to DensityBand.PRODUCE,
                "omelette" to DensityBand.PRODUCE,
            )
        for ((label, band) in expected) {
            assertEquals(band, FoodRails.bandFor(label), "band for $label")
        }
    }

    @Test
    public fun unknownLabelsGetTheAbsoluteCeilingNeverALooserGuess() {
        assertEquals(DensityBand.UNKNOWN, FoodRails.bandFor(" grandma's mystery casserole "))
        assertEquals(FoodRails.MAX_KCAL_PER_GRAM, DensityBand.UNKNOWN.kcalPerGram)
    }

    // --- the mass × density ceiling -----------------------------------------

    @Test
    public fun withinTheCeilingNothingChanges() {
        val outcome = FoodRails.clampEnergy(label = "pizza", grams = 200.0, estimatedKcal = 500.0)
        assertEquals(500.0, outcome.kcal, 1e-9)
        assertFalse(outcome.tripped)
    }

    @Test
    public fun starchRailClampsAtFourKcalPerGram() {
        // 300 g "pizza" claiming 27M kcal → clamped to 300 × 4 = 1200 kcal.
        val outcome = FoodRails.clampEnergy(label = "pizza", grams = 300.0, estimatedKcal = 27_000_000.0)
        assertEquals(1200.0, outcome.kcal, 1e-9)
        assertTrue(outcome.tripped)
        assertEquals(DensityBand.STARCHES, outcome.band)
    }

    @Test
    public fun unknownClassNeverExceedsPureFat() {
        val outcome = FoodRails.clampEnergy(label = "mystery dish", grams = 100.0, estimatedKcal = 27_000_000.0)
        assertEquals(900.0, outcome.kcal, 1e-9)
        assertTrue(outcome.tripped)
    }

    @Test
    public fun tripCopyNamesTheBandAndIsNeverBlaming() {
        val outcome = FoodRails.clampEnergy(label = "french_fries", grams = 250.0, estimatedKcal = 9_000.0)
        val copy = FoodRails.tripCopy(outcome)
        assertTrue(copy.contains("cooked starch"), copy)
        assertTrue(copy.contains("250 g"), copy)
        assertFalse(copy.contains("you should"), copy)
        assertFalse(copy.contains("too much"), copy)
    }

    @Test
    public fun confidenceDowngradesByHalfOnTrip() {
        assertEquals(0.4, FoodRails.downgrade(0.8)!!, 1e-9)
        assertEquals(null, FoodRails.downgrade(null))
    }

    @Test
    public fun densityClampCapsThePer100gHintAtTheBandCeiling() {
        // A mislabeled "butter" hinting 1200 kcal/100 g clamps to pure fat's 900.
        assertEquals(900.0, FoodRails.clampDensity("butter", 1200.0), 1e-9)
        assertEquals(266.0, FoodRails.clampDensity("pizza", 266.0), 1e-9)
        // And the clamped density then satisfies the band check everywhere.
        assertTrue(FoodRails.withinBandDensity("butter", FoodRails.clampDensity("butter", 1200.0)))
    }

    @Test
    public fun densityClampComposesWithTheMassCeiling() {
        val clampedDensity = FoodRails.clampDensity("mystery dish", 1200.0)
        val portionKcal = clampedDensity * 250.0 / 100.0
        val outcome = FoodRails.clampEnergy("mystery dish", 250.0, portionKcal)
        assertFalse(outcome.tripped, "a density-clamped estimate can never trip the mass ceiling")
    }

    // --- property tests (T-J): the invariant IS the guarantee ----------------

    @Test
    public fun clampedKcalNeverExceedsGramsTimesBandCeiling() =
        runTest {
            val labels =
                listOf(
                    "pizza",
                    "butter",
                    "ice_cream",
                    "grilled_salmon",
                    "cheese_plate",
                    "greek_salad",
                    "peanut_butter",
                    "mystery dish",
                )
            checkAll(
                Arb.numericDouble(0.1, 5_000.0),
                Arb.numericDouble(0.0, 30_000_000.0),
            ) { grams, kcal ->
                for (label in labels) {
                    val outcome = FoodRails.clampEnergy(label, grams, kcal)
                    assertTrue(
                        outcome.kcal <= grams * outcome.band.kcalPerGram + 1e-6,
                        "kcal ${outcome.kcal} exceeds the ${outcome.band} ceiling for ${grams}g ($label)",
                    )
                    assertTrue(outcome.kcal >= 0.0)
                }
            }
        }

    @Test
    public fun clampingIsMonotoneAndIdempotent() =
        runTest {
            checkAll(
                Arb.numericDouble(10.0, 1_000.0),
                Arb.numericDouble(0.0, 50_000.0),
            ) { grams, kcal ->
                val first = FoodRails.clampEnergy("hamburger", grams, kcal)
                val again = FoodRails.clampEnergy("hamburger", grams, first.kcal)
                assertEquals(first.kcal, again.kcal, 1e-9, "clamping twice must not move the number")
                assertFalse(again.tripped, "a clamped value sits at the ceiling, never beyond it")
                assertTrue(first.kcal <= min(kcal, grams * 5.0) + 1e-6)
            }
        }
}
