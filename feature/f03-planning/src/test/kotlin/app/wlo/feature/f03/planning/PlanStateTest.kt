package app.wlo.feature.f03.planning

import app.wlo.feature.f03.planning.state.RecipeForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The plan surface's pure render words: the R-S7 tolerance split, the recipe
 * form's validity gate, and the honest state vocabulary. These pin the words
 * the week grid speaks before any Compose is involved.
 */
public class PlanStateTest {
    // NOTE: stateWord/trim1 live in the ui package (internal); the form gate and
    // tolerance constant are the public pure surface — pinned here.

    @Test
    public fun recipeForm_rejectsIncompleteForms() {
        val empty =
            app.wlo.feature.f03.planning.state
                .RecipeForm()
        assertFalse(empty.valid, "an empty form is not a recipe")

        val nameOnly = empty.copy(name = "Lentil curry")
        assertFalse(nameOnly.valid, "a name alone is not a recipe")

        val withSlots = nameOnly.copy(mealSlots = setOf("dinner"))
        assertFalse(withSlots.valid, "macros are still missing")

        val full =
            withSlots.copy(
                kcal = "620",
                proteinG = "31",
                carbG = "58",
                fatG = "22",
                fiberG = "12",
            )
        assertTrue(full.valid, "name + slots + non-negative macros validate")
    }

    @Test
    public fun recipeForm_negativeNumbersNeverValidate() {
        val negative =
            RecipeForm(
                name = "x",
                servingsBase = "2",
                mealSlots = setOf("lunch"),
                kcal = "-5",
                proteinG = "1",
                carbG = "1",
                fatG = "1",
                fiberG = "1",
            )
        assertFalse(negative.valid)
    }

    @Test
    public fun planConstants_carryTheV1Contract() {
        val vm = app.wlo.feature.f03.planning.state.PlanViewModel
        assertEquals(listOf("breakfast", "lunch", "dinner"), vm.PLAN_SLOTS)
        assertEquals(7, app.wlo.feature.f03.planning.state.PlanViewModel.PLAN_DAYS)
        assertEquals(
            5.0,
            app.wlo.feature.f03.planning.state.PlanViewModel.KCAL_TOLERANCE_PCT,
            "R-S7 pins ±5% kcal",
        )
    }
}
