package app.wlo.core.engines

import app.wlo.core.model.NutritionPerServing
import app.wlo.core.model.Recipe
import app.wlo.core.model.RecipeIngredient
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM timing smoke test for the F03 re-deal budget (ARCHITECTURE §2.6:
 * "live re-deal ≤ 100 ms" — F03 §4 re-runs generation live as the settings
 * dials move). A generous wall-clock bound holds the honest engineering
 * budget in CI; the precise emulator baseline is recorded by the
 * `:benchmarks` PlannerReDealBenchmark (macrobenchmark module, M5).
 */
class PlannerReDealTimingTest {
    @Test
    fun threeHundredRecipesReDealWellUnderTheBudget() {
        val library = syntheticLibrary(size = 300)
        val request =
            PlannerEngine.PlanRequest(
                startDayEpochDay = 20_700,
                days = 7,
                slots = listOf("breakfast", "lunch", "dinner", "snack"),
                targets = (0 until 7).map { PlannerEngine.DayTargets(20_700L + it, kcal = 1_900.0, fiberG = 28.0) },
                seed = 42L,
            )

        // JIT warm-up, then three measured re-deals; every one must respect the budget.
        repeat(5) { PlannerEngine.generate(library, request) }
        val times =
            (1..3).map {
                measureNanoTime { PlannerEngine.generate(library, request) } / 1_000.0 / 1_000.0
            }
        times.forEach { ms ->
            assertTrue(ms < 100.0, "re-deal took $ms ms — over the 100 ms live-preview budget")
        }
        assertTrue(times.average() < 100.0, "mean re-deal ${times.average()} ms")
    }

    @Test
    fun reDealIsPureOutputsMatchForTheSameInputs() {
        val library = syntheticLibrary(size = 60)
        val request =
            PlannerEngine.PlanRequest(
                startDayEpochDay = 20_700,
                days = 7,
                slots = listOf("breakfast", "lunch", "dinner"),
                targets = (0 until 7).map { PlannerEngine.DayTargets(20_700L + it, kcal = 1_800.0) },
                seed = 7L,
            )
        assertEquals(
            PlannerEngine.generate(library, request).slots,
            PlannerEngine.generate(library, request).slots,
        )
    }

    /** Deterministic synthetic library: 4 slot families × varied kcal bands. */
    private fun syntheticLibrary(size: Int): List<Recipe> {
        val random = Random(1)
        val slots = listOf("breakfast", "lunch", "dinner", "snack")
        val budgets = mapOf("breakfast" to 475.0, "lunch" to 665.0, "dinner" to 760.0, "snack" to 285.0)
        return (0 until size).map { index ->
            val slot = slots[index % slots.size]
            val kcal = budgets.getValue(slot) * (0.72 + random.nextDouble() * 0.56)
            Recipe(
                id = "syn-$index",
                name = "Synthetic $index",
                servingsBase = 1.0,
                slots = listOf(slot),
                tags = if (index % 3 == 0) listOf("vegetarian") else emptyList(),
                fodmapTags = if (index % 7 == 0) listOf("fructans") else emptyList(),
                ingredients =
                    (1..4).map { ing ->
                        RecipeIngredient("item-$ing", "Item $ing", 100.0, "g")
                    },
                nutrition =
                    NutritionPerServing(
                        kcal = kcal,
                        proteinG = kcal * 0.05,
                        carbG = kcal * 0.11,
                        fatG = kcal * 0.03,
                        fiberG = 6.0 + index % 12,
                    ),
            )
        }
    }
}
