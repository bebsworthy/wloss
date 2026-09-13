package app.wlo.core.engines

import app.wlo.core.model.FodmapTags
import app.wlo.core.model.NutritionPerServing
import app.wlo.core.model.Recipe
import app.wlo.core.testing.GoldenFixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Golden-file harness for the F03 planner (F03 §3; R-S7 badge, R-B3 fiber,
 * R-B4 honest gating). Three constraint families run unchanged in CI:
 * vegetarian + high-fiber (a full week must fit within the ±5 % badge),
 * low-FODMAP (tag filtering with a small honest pool), and calorie-floor
 * (an HONEST MISS: unfillable slots carry their reason, never a blank).
 */
class PlannerEngineGoldenTest {
    @Serializable
    private data class Fixture(
        val recipes: List<FixtureRecipe>,
        val scenarios: List<Scenario>,
    )

    @Serializable
    private data class FixtureRecipe(
        val id: String,
        val slots: List<String>,
        val tags: List<String> = emptyList(),
        val fodmapTags: List<String> = emptyList(),
        val servingsBase: Double = 1.0,
        val kcal: Double,
        val proteinG: Double,
        val carbG: Double,
        val fatG: Double,
        val fiberG: Double,
    )

    @Serializable
    private data class FixtureConstraints(
        val requireTags: List<String> = emptyList(),
        val allergies: List<String> = emptyList(),
        val exclusions: List<String> = emptyList(),
        val dislikes: List<String> = emptyList(),
        val forbidTags: List<String> = emptyList(),
        val lowFodmap: Boolean = false,
        val minKcalPerSlot: Double = 0.0,
    )

    @Serializable
    private data class Scenario(
        val name: String,
        val days: Int,
        val slots: List<String>,
        val dayKcal: Double,
        val fiberG: Double? = null,
        val seed: Long,
        val libraryPrefix: String,
        val constraints: FixtureConstraints = FixtureConstraints(),
        val expectedFilledSlots: Int? = null,
        val expectedUnfilledSlots: Int? = null,
        val expectUnfilledReason: String? = null,
        val expectedFitDaysMin: Int? = null,
        val expectedDistinctRecipesMax: Int? = null,
        val expectedActiveFodmapRecipes: Int? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val fixture: Fixture by lazy {
        json.decodeFromString<Fixture>(GoldenFixtures.load("golden/planner_scenarios.json"))
    }

    private fun recipesFor(scenario: Scenario): List<Recipe> =
        fixture.recipes
            .filter { it.id.startsWith(scenario.libraryPrefix) }
            .map { r ->
                Recipe(
                    id = r.id,
                    name = r.id,
                    servingsBase = r.servingsBase,
                    slots = r.slots,
                    tags = r.tags,
                    fodmapTags = r.fodmapTags,
                    nutrition =
                        NutritionPerServing(
                            kcal = r.kcal,
                            proteinG = r.proteinG,
                            carbG = r.carbG,
                            fatG = r.fatG,
                            fiberG = r.fiberG,
                        ),
                )
            }

    private fun run(scenario: Scenario): PlannerEngine.GeneratedPlan =
        PlannerEngine.generate(
            recipesFor(scenario),
            PlannerEngine.PlanRequest(
                startDayEpochDay = 20_700,
                days = scenario.days,
                slots = scenario.slots,
                targets =
                    (0 until scenario.days).map { offset ->
                        PlannerEngine.DayTargets(
                            dayEpochDay = 20_700L + offset,
                            kcal = scenario.dayKcal,
                            fiberG = scenario.fiberG,
                        )
                    },
                constraints =
                    PlannerEngine.Constraints(
                        allergies = scenario.constraints.allergies,
                        exclusions = scenario.constraints.exclusions,
                        dislikes = scenario.constraints.dislikes,
                        requireTags = scenario.constraints.requireTags,
                        forbidTags = scenario.constraints.forbidTags,
                        lowFodmap = scenario.constraints.lowFodmap,
                        minKcalPerSlot = scenario.constraints.minKcalPerSlot,
                    ),
                seed = scenario.seed,
            ),
        )

    @Test
    fun goldenScenariosMatchThePinnedBehavior() {
        assertTrue(fixture.scenarios.size >= 3, "the fixture carries 3+ constraint families")
        fixture.scenarios.forEach { scenario ->
            val plan = run(scenario)
            val filled = plan.slots.count { it.recipeId != null }

            scenario.expectedFilledSlots?.let {
                assertEquals(it, filled, "${scenario.name}: filled slots")
            }
            scenario.expectedUnfilledSlots?.let {
                assertEquals(it, plan.report.unfillableSlots, "${scenario.name}: unfilled slots")
            }
            scenario.expectedFitDaysMin?.let {
                val fitDays = plan.fits.count { fit -> fit.kcalWithinTolerance }
                assertTrue(
                    fitDays >= it,
                    "${scenario.name}: expected ≥ $it days within the ±5 % badge, saw $fitDays " +
                        "(${plan.fits.map { f -> "${f.kcalDeltaPct}%" }})",
                )
            }
            scenario.expectedDistinctRecipesMax?.let {
                assertTrue(
                    plan.report.distinctRecipes <= it,
                    "${scenario.name}: variety/overlap balance collapsed into monotony",
                )
            }
            scenario.expectedActiveFodmapRecipes?.let {
                val activeFodmap =
                    plan.slots
                        .mapNotNull { it.recipeId }
                        .distinct()
                        .mapNotNull { id -> recipesFor(scenario).firstOrNull { it.id == id } }
                        .count { r -> r.fodmapTags.any { it != FodmapTags.UNKNOWN } }
                assertEquals(it, activeFodmap, "${scenario.name}: active-FODMAP recipes must be filtered")
            }
            scenario.expectUnfilledReason?.let {
                val reasons = plan.slots.mapNotNull { it.unfillableReason }
                assertTrue(
                    reasons.isNotEmpty() && reasons.all { r -> r.contains(it, ignoreCase = true) },
                    "${scenario.name}: unfillable slots must explain themselves honestly",
                )
            }

            // Every plan is honest: filled + unfillable == slots dealt, and the
            // report agrees with the slot rows (F03 §4 data-quality gating).
            assertEquals(
                plan.slots.size,
                plan.report.filledSlots + plan.report.unfillableSlots,
                "${scenario.name}: report/slot accounting",
            )
        }
    }

    @Test
    fun sameSeedSameInputsIsTheSamePlan() {
        val scenario = fixture.scenarios.first()
        val first = run(scenario)
        val second = run(scenario)
        assertEquals(
            DecisionLedger.json.encodeToString(PlannerEngine.GeneratedPlan.serializer(), first),
            DecisionLedger.json.encodeToString(PlannerEngine.GeneratedPlan.serializer(), second),
            "deterministic given (recipes, request): same seed must re-deal identically",
        )
    }

    @Test
    fun seedChangesTheDealNotTheRules() {
        val scenario = fixture.scenarios.first()
        val base = run(scenario)
        val reRolled =
            PlannerEngine.generate(
                recipesFor(scenario),
                PlannerEngine.PlanRequest(
                    startDayEpochDay = 20_700,
                    days = scenario.days,
                    slots = scenario.slots,
                    targets =
                        (0 until scenario.days).map {
                            PlannerEngine.DayTargets(
                                20_700L + it,
                                scenario.dayKcal,
                                fiberG = scenario.fiberG,
                            )
                        },
                    constraints =
                        PlannerEngine.Constraints(requireTags = scenario.constraints.requireTags),
                    seed = scenario.seed + 1,
                ),
            )
        // Rules hold regardless of seed: hard filters, fills, fit honesty.
        assertEquals(base.report.filledSlots, reRolled.report.filledSlots)
        reRolled.slots.forEach { slot ->
            assertNotNull(slot.recipeId, "a seeded re-deal never drops slots the library can fill")
        }
    }

    @Test
    fun badgeMathFollowsTheRuling() {
        // R-S7: ±5 % kcal; the badge carries gram deltas, never verdicts.
        val badge =
            PlannerEngine.badgeFor(
                kcal = 1_040.0,
                proteinG = 90.0,
                fiberG = 20.0,
                targets = PlannerEngine.DayTargets(1, kcal = 1_000.0, proteinG = 80.0, fiberG = 30.0),
            )
        assertTrue(badge.kcalWithinTolerance, "+4 % is within the ±5 % badge")
        assertEquals(4.0, badge.kcalDeltaPct)
        assertEquals(10.0, badge.proteinDeltaG)
        assertEquals(-10.0, badge.fiberDeltaG)

        val outside = PlannerEngine.badgeFor(1_100.0, 0.0, 0.0, PlannerEngine.DayTargets(1, 1_000.0))
        assertTrue(!outside.kcalWithinTolerance, "+10 % shows the gap")
    }
}
