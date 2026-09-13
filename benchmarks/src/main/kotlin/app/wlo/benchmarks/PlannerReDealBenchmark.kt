package app.wlo.benchmarks

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wlo.core.engines.PlannerEngine
import app.wlo.core.model.NutritionPerServing
import app.wlo.core.model.Recipe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * The F03 live re-deal baseline (F03 §4: settings-dial previews re-run
 * generation live at ≤ 100 ms; ARCHITECTURE §2.6 budget). Compiles the
 * benchmark APK like a release build (`benchmark` buildType) and measures
 * full `generate` deals over a 300-recipe library — the realistic v1 library
 * upper bound (R-S3 seed + imports).
 *
 * Baseline config: CompilationMode-equivalent = `benchmark` buildType
 * (non-debuggable, no test coverage), 5 warm-up deals before measurement.
 * The recorded number lives in the milestone report; CI wiring is optional
 * per the M5 brief (WLO-0014 functional baseline, not a budget gate yet).
 */
@RunWith(AndroidJUnit4::class)
class PlannerReDealBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val library: List<Recipe> = syntheticLibrary(size = 300)

    @Test
    fun reDealWeek() {
        benchmarkRule.measureRepeated {
            PlannerEngine.generate(library, weekRequest(seed = 42))
        }
    }

    @Test
    fun reDealWeekSecondSeed() {
        benchmarkRule.measureRepeated {
            PlannerEngine.generate(library, weekRequest(seed = 7))
        }
    }

    // --- the fixed input (deterministic; mirrors the JVM timing smoke test) ---

    private fun weekRequest(seed: Long): PlannerEngine.PlanRequest =
        PlannerEngine.PlanRequest(
            startDayEpochDay = 20_700,
            days = 7,
            slots = listOf("breakfast", "lunch", "dinner", "snack"),
            targets =
                (0 until 7).map { offset ->
                    PlannerEngine.DayTargets(
                        dayEpochDay = 20_700L + offset,
                        kcal = 1_900.0,
                        proteinG = 110.0,
                        fiberG = 28.0,
                    )
                },
            seed = seed,
        )

    private fun syntheticLibrary(size: Int): List<Recipe> {
        val random = Random(1)
        val slots = listOf("breakfast", "lunch", "dinner", "snack")
        val budgets =
            mapOf(
                "breakfast" to 475.0,
                "lunch" to 665.0,
                "dinner" to 760.0,
                "snack" to 285.0,
            )
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
                        app.wlo.core.model
                            .RecipeIngredient("item-$ing", "Item $ing", 100.0, "g")
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
