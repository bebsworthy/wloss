package app.wlo.core.data

import app.wlo.core.model.Aisle
import app.wlo.core.model.FodmapTags
import app.wlo.core.model.MeasureUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The R-S3 seed-content validator: the shipped CC0 bundle is programmatically
 * checked, not trusted — schema conformance, macro arithmetic consistency
 * (ingredients → per-serving within tolerance via Atwater), tag vocabulary
 * (incl. the R-S8 six FODMAP tags), aisle coverage of the shipped taxonomy,
 * license fields, and pool health for the planner's constraint families.
 */
class RecipeSeedValidatorTest {
    private val library: WloSeedLibrary =
        checkNotNull(RecipeSeed.load(RecipeSeed.defaultReader)) {
            "the seed bundle must load from the module resources"
        }

    // --- bundle level ----------------------------------------------------------

    @Test
    fun bundleCarriesTheRuledContentShape() {
        assertEquals(1, library.schemaVersion)
        assertEquals("CC0", library.license)
        assertTrue(library.recipes.size >= 50, "R-S3: ~50 starter recipes (saw ${library.recipes.size})")
        assertTrue(library.groceryItems.size >= 100, "the canonical grocery catalog backs the recipes")
        assertEquals(
            library.recipes
                .map { it.id }
                .distinct()
                .size,
            library.recipes.size,
            "recipe ids are unique",
        )
        assertEquals(
            library.groceryItems
                .map { it.id }
                .distinct()
                .size,
            library.groceryItems.size,
            "grocery ids are unique",
        )
    }

    @Test
    fun everyRecipeConformsToTheSchema() {
        library.recipes.forEach { recipe ->
            assertTrue(recipe.servingsBase > 0, "${recipe.id}: servingsBase positive (no 2/4/6 straitjacket)")
            assertTrue(recipe.slots.isNotEmpty(), "${recipe.id}: at least one meal slot")
            assertTrue(recipe.slots.all { it in setOf("breakfast", "lunch", "dinner", "snack", "drink") }, "${recipe.id}: slot vocabulary")
            assertTrue(recipe.ingredients.isNotEmpty(), "${recipe.id}: has ingredient lines")
            assertTrue(recipe.steps.isNotEmpty(), "${recipe.id}: has steps")
            assertTrue(recipe.nutrition.kcal > 0, "${recipe.id}: kcal positive")
            assertEquals("seed", recipe.source, "${recipe.id}: seed provenance")
            assertEquals("CC0", recipe.license, "${recipe.id}: R-S3 license on every recipe")
            recipe.ingredients.forEach { ingredient ->
                assertTrue(
                    library.groceryItems.any {
                        it.id == ingredient.groceryItemId
                    },
                    "${recipe.id}: ingredient ${ingredient.groceryItemId} resolves in the canonical catalog",
                )
                assertTrue(ingredient.qty > 0, "${recipe.id}: ${ingredient.groceryItemId} qty positive")
                assertNotNullUnit(ingredient.unit, recipe.id)
            }
        }
    }

    private fun assertNotNullUnit(
        unit: String,
        recipeId: String,
    ) {
        assertTrue(
            MeasureUnit.fromWireName(unit) != null,
            "$recipeId: unit '$unit' must be a canonical MeasureUnit",
        )
    }

    @Test
    fun aislesCoverTheShippedTaxonomy() {
        library.groceryItems.forEach { item ->
            assertTrue(
                Aisle.fromWireName(item.aisle) != null,
                "${item.id}: aisle '${item.aisle}' must be a shipped taxonomy tag",
            )
            assertTrue(item.unit.let { MeasureUnit.fromWireName(it) != null }, "${item.id}: default unit is canonical")
            assertTrue(item.per100 != null || item.facets.isNotEmpty(), "${item.id}: carries nutrition or facets")
        }
        // The shipped catalog paints the whole store (minus the honest bucket).
        val used = library.groceryItems.map { it.aisle }.toSet()
        listOf("produce", "dairy", "pantry").forEach { aisle ->
            assertTrue(used.contains(aisle), "the catalog covers the $aisle aisle")
        }
    }

    @Test
    fun fodmapTagsStayInTheRs8Vocabulary() {
        library.recipes.forEach { recipe ->
            recipe.fodmapTags.forEach { tag ->
                assertTrue(
                    FodmapTags.ALL.contains(tag),
                    "${recipe.id}: FODMAP tag '$tag' must be one of the shipped six (${FodmapTags.ALL})",
                )
            }
        }
    }

    @Test
    fun macroArithmeticIsConsistentIngredientsToPerServing() {
        val items = library.groceryItems.associateBy { it.id }
        library.recipes.forEach { recipe ->
            var p = 0.0
            var c = 0.0
            var f = 0.0
            var fiber = 0.0
            recipe.ingredients.forEach { ingredient ->
                val item = checkNotNull(items[ingredient.groceryItemId]) { ingredient.groceryItemId }
                val per100 = checkNotNull(item.per100) { "${item.id} lacks the per-100 table" }
                val density = item.densityGPerMl ?: 1.0
                val grams =
                    when (MeasureUnit.fromWireName(ingredient.unit)) {
                        MeasureUnit.GRAM -> ingredient.qty
                        MeasureUnit.MILLILITER -> ingredient.qty * density
                        MeasureUnit.LITER -> ingredient.qty * 1000.0 * density
                        MeasureUnit.KILOGRAM -> ingredient.qty * 1000.0
                        MeasureUnit.OUNCE -> ingredient.qty * 28.349523125
                        MeasureUnit.POUND -> ingredient.qty * 453.59237
                        MeasureUnit.CUP -> ingredient.qty * 240.0 * density
                        MeasureUnit.TABLESPOON -> ingredient.qty * 15.0 * density
                        MeasureUnit.TEASPOON -> ingredient.qty * 5.0 * density
                        MeasureUnit.COUNT -> ingredient.qty * (item.gramsPerPiece ?: 100.0)
                        null -> error("bad unit in ${recipe.id}")
                    }
                val k = grams / 100.0
                p += per100.proteinG * k
                c += per100.carbG * k
                f += per100.fatG * k
                fiber += per100.fiberG * k
            }
            val servings = recipe.servingsBase
            val n = recipe.nutrition
            val atwater = 4 * p / servings + 4 * c / servings + 9 * f / servings + 2 * fiber / servings
            assertTrue(
                kotlin.math.abs(n.kcal - atwater) <= kotlin.math.abs(atwater) * 0.02 + 2.0,
                "${recipe.id}: kcal ${n.kcal} vs ingredient Atwater ${"%.1f".format(atwater)}",
            )
            assertClose(recipe.id, "proteinG", n.proteinG, p / servings, 1.0)
            assertClose(recipe.id, "carbG", n.carbG, c / servings, 1.0)
            assertClose(recipe.id, "fatG", n.fatG, f / servings, 1.0)
            assertClose(recipe.id, "fiberG", n.fiberG, fiber / servings, 1.0)
            // Honest portion sizes for seed content.
            assertTrue(n.kcal in 60.0..950.0, "${recipe.id}: per-serving kcal ${n.kcal} plausible")
        }
    }

    @Test
    fun dietTagsAreHonestAboutTheFacets() {
        val facetsByItem = library.groceryItems.associate { it.id to it.facets.toSet() }
        library.recipes.forEach { recipe ->
            val facets =
                recipe.ingredients
                    .flatMap { facetsByItem[it.groceryItemId].orEmpty() }
                    .toSet()
            if (recipe.tags.contains("vegetarian") || recipe.tags.contains("vegan")) {
                assertTrue(
                    facets.none { it == "meat" || it == "fish" || it == "shellfish" },
                    "${recipe.id}: vegetarian/vegan recipes carry no animal flesh",
                )
            }
            if (recipe.tags.contains("vegan")) {
                assertTrue(
                    facets.none { it == "egg" || it == "lactose" || it == "honey" },
                    "${recipe.id}: vegan recipes carry no dairy/egg/honey",
                )
            }
            if (recipe.tags.contains("high-fiber")) {
                assertTrue(
                    recipe.nutrition.fiberG >= 8.0,
                    "${recipe.id}: 'high-fiber' means ≥ 8 g/serving (saw ${recipe.nutrition.fiberG})",
                )
            }
        }
    }

    @Test
    fun poolHealthSupportsThePlannerFamilies() {
        val bySlot = library.recipes.flatMap { r -> r.slots.map { it to r } }.groupBy({ it.first }, { it.second })
        assertTrue((bySlot["breakfast"].orEmpty()).size >= 10, "breakfast pool depth")
        assertTrue((bySlot["lunch"].orEmpty()).size >= 15, "lunch pool depth")
        assertTrue((bySlot["dinner"].orEmpty()).size >= 20, "dinner pool depth")

        val lowFodmap = library.recipes.filter { r -> r.fodmapTags.none { it != FodmapTags.UNKNOWN } }
        assertTrue(lowFodmap.size >= 8, "the low-FODMAP family has a real pool (${lowFodmap.size})")
        listOf("breakfast", "lunch", "dinner").forEach { slot ->
            assertTrue(
                lowFodmap.any { it.slots.contains(slot) },
                "low-FODMAP pool covers the $slot slot",
            )
        }
        assertTrue(
            library.recipes.count { it.tags.contains("vegetarian") || it.tags.contains("vegan") } >= 20,
            "the vegetarian family has a real pool",
        )
        assertTrue(library.recipes.count { it.tags.contains("batch") } >= 8, "cook-once leftovers candidates exist")
        assertTrue(
            library.recipes
                .map { it.cuisine }
                .distinct()
                .size >= 6,
            "plausible cuisine spread",
        )
    }

    private fun assertClose(
        recipeId: String,
        field: String,
        declared: Double,
        computed: Double,
        tolerance: Double,
    ) {
        assertTrue(
            kotlin.math.abs(declared - computed) <= tolerance,
            "$recipeId: $field declared $declared vs computed ${"%.2f".format(computed)}",
        )
    }
}
