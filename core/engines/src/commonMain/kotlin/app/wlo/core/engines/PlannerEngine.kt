package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.PlannedSlot
import app.wlo.core.model.PlannedSlotState
import app.wlo.core.model.Recipe
import app.wlo.core.model.RecipeId
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * F03 on-device generation engine (F03 §3, deterministic per R-S10; D7-pure:
 * instant-in / value-out, no clock, no IO, no globals).
 *
 * Pipeline (F03 §3 "Processing"):
 *  1. **Filter** recipes by hard rules — allergies hard-block (F03 §9), the
 *     other exclusions hard-filter, required/forbidden tags, FODMAP mode
 *     (R-S8 tags), and meal-slot suitability.
 *  2. **Score** survivors for macro fit against each day's slot budget
 *     (calorie distance + protein/fiber gap vs the day targets, R-B3 fiber).
 *  3. **Optimize ingredient overlap** across the week (Mealime's waste-aware
 *     reuse) with a greedy deal + bounded pairwise-refinement pass.
 *  4. **Slot leftovers**: a recipe the solver repeats later in the week
 *     becomes a cook event; the repeat becomes a leftover instance charged to
 *     the cook day (ingredients pay for cook events, not eating events).
 *  5. **Report**: per-day fit deltas ([FitBadge], ±5 % kcal per R-S7), the
 *     overlap score, unfillable slots with their reason, and the
 *     variety-vs-overlap tension flag.
 *
 * Determinism: identical (recipes, request) → identical plan, always. The
 * seed feeds a stable PRNG used only to shuffle equal-scoring candidates, so
 * different seeds yield different (equally valid) weeks — the "variety" dial.
 *
 * Complexity: filter O(R·L) (R recipes, L ingredient lines), greedy O(S·R),
 * pairwise bounded by [ConstantsRegistry.PLANNER_PAIRWISE_SWAP_BUDGET] swap
 * evaluations — a few hundred recipes re-deal in well under the 100 ms live
 * preview budget (ARCHITECTURE §2.6; see :benchmarks PlannerReDealBenchmark).
 */
public object PlannerEngine {
    public const val VERSION: String = ConstantsRegistry.PLANNER_FORMULA_VERSION

    // --- Inputs -----------------------------------------------------------------

    /** User-tunable generation dials (F03 §3 "Generation settings"). */
    @Serializable
    public data class Settings(
        /** Servings each slot is planned for (household default, F01). */
        public val defaultServings: Double = 1.0,
        /** How strongly recently-planned recipes are down-weighted (0 = off). */
        public val varietyBias: Double = ConstantsRegistry.PLANNER_VARIETY_BIAS_DEFAULT,
        /** Ingredient-overlap objective weight (0 = plan every meal independently). */
        public val overlapWeight: Double = ConstantsRegistry.PLANNER_OVERLAP_WEIGHT_DEFAULT,
        /** Cooking-frequency cap (F03 §3: "max 3 cook events/week"). */
        public val maxCookEventsPerWeek: Int = ConstantsRegistry.PLANNER_COOK_EVENTS_CAP_DEFAULT,
        /** Master switch for the cook-once-eat-twice objective. */
        public val leftoversEnabled: Boolean = true,
    )

    /**
     * Hard/soft rule set for one generation (F03 §3 step 1 + §9 allergen
     * policy). Allergies and exclusions HARD-filter; dislikes only down-weight.
     */
    @Serializable
    public data class Constraints(
        /** True allergies — block generation and swap suggestions outright (F03 §9). */
        public val allergies: List<String> = emptyList(),
        /** Dietary/ethical exclusions — hard filters. */
        public val exclusions: List<String> = emptyList(),
        /** Dislikes — never suggested, never forbidden (soft score penalty). */
        public val dislikes: List<String> = emptyList(),
        /** Diet facets every recipe must carry (e.g. ["vegetarian"]). */
        public val requireTags: List<String> = emptyList(),
        /** Diet facets no recipe may carry. */
        public val forbidTags: List<String> = emptyList(),
        /** Low-FODMAP week: recipes carrying any active R-S8 tag are filtered. */
        public val lowFodmap: Boolean = false,
        /** kcal floor per slot — the "calorie-floor" constraint family (golden fixture 3). */
        public val minKcalPerSlot: Double = 0.0,
    )

    /** Resolved targets for one day (A.3 day projection values, already per-day). */
    @Serializable
    public data class DayTargets(
        public val dayEpochDay: Long,
        public val kcal: Double,
        public val proteinG: Double? = null,
        public val carbG: Double? = null,
        public val fatG: Double? = null,
        /** R-B3: exactly one fiber number; divergence renders as a plan-fit signal. */
        public val fiberG: Double? = null,
    )

    /** One generation request: a date range, its targets, the rules, the seed. */
    @Serializable
    public data class PlanRequest(
        public val startDayEpochDay: Long,
        public val days: Int = 7,
        /** Meal-slot wire names filled each day, in render order. */
        public val slots: List<String> = listOf("breakfast", "lunch", "dinner"),
        /** One entry per day of the range (missing days get no slots — never a guess). */
        public val targets: List<DayTargets> = emptyList(),
        public val settings: Settings = Settings(),
        public val constraints: Constraints = Constraints(),
        /** Variety seed: same inputs + same seed → same plan; a new seed re-rolls. */
        public val seed: Long = 0L,
    )

    // --- Outputs ----------------------------------------------------------------

    /**
     * The day-fit badge (R-S7): ✓ within ±5 % kcal; macro gaps render as gram
     * deltas ("P −6 g") with a ✓ inside ±[ConstantsRegistry.PLANNER_MACRO_TOLERANCE_PCT].
     * The badge shows the gap, never a verdict (F03 §5).
     */
    @Serializable
    public data class FitBadge(
        public val targetKcal: Double,
        public val plannedKcal: Double,
        public val kcalDeltaPct: Double,
        public val kcalWithinTolerance: Boolean,
        public val proteinTargetG: Double? = null,
        public val proteinDeltaG: Double? = null,
        public val fiberTargetG: Double? = null,
        public val fiberDeltaG: Double? = null,
    ) {
        public val proteinWithinTolerance: Boolean
            get() =
                proteinTargetG == null ||
                    proteinDeltaG == null ||
                    kotlin.math.abs(proteinDeltaG) <= proteinTargetG * ConstantsRegistry.PLANNER_MACRO_TOLERANCE_PCT / 100.0

        public val fiberWithinTolerance: Boolean
            get() =
                fiberTargetG == null ||
                    fiberDeltaG == null ||
                    kotlin.math.abs(fiberDeltaG) <= fiberTargetG * ConstantsRegistry.PLANNER_MACRO_TOLERANCE_PCT / 100.0
    }

    /** One dealt slot (engine output; the repository persists it as a [PlannedSlot]). */
    @Serializable
    public data class SlotDraft(
        public val dayEpochDay: Long,
        public val mealSlot: String,
        /** Null = unfillable slot — the honest "add anything" card with [unfillableReason]. */
        public val recipeId: RecipeId? = null,
        public val recipeVersion: Int? = null,
        /** Servings EATEN at this slot — nutrition scales by this, always. */
        public val servings: Double = 1.0,
        /** Cook event emitting a batch (ingredients charged here, F03 §3). */
        public val isCookEvent: Boolean = false,
        /**
         * Total batch the cook event cooks (eaten-here + leftovers). Only the
         * ingredient expansion scales by it — nutrition follows eating slots.
         */
        public val batchServings: Double? = null,
        /** Leftover instance eating from the cook event's batch (no ingredients of its own). */
        public val parentCookDay: Long? = null,
        public val unfillableReason: String? = null,
    )

    /** Plan-level report (F03 §3 step 5 — the "why this plan" panel). */
    @Serializable
    public data class Report(
        public val filledSlots: Int,
        public val unfillableSlots: Int,
        public val distinctRecipes: Int,
        /** Ingredient lines shared by ≥ 2 distinct recipes in the plan. */
        public val sharedIngredients: Int,
        public val overlapScore: Double,
        public val cookEvents: Int,
        public val leftoverServings: Double,
        /** True when the variety and overlap objectives pulled against each other (F03 §3). */
        public val objectivesConflict: Boolean,
        /** The constraints that bound hardest, most-binding first (the explainer panel). */
        public val hardestRules: List<String> = emptyList(),
    )

    @Serializable
    public data class GeneratedPlan(
        public val slots: List<SlotDraft>,
        public val fits: List<FitBadge>,
        public val report: Report,
    )

    // --- Generation -------------------------------------------------------------

    public fun generate(
        recipes: List<Recipe>,
        request: PlanRequest,
    ): GeneratedPlan {
        val random = Random(request.seed)
        val targetsByDay = request.targets.associateBy { it.dayEpochDay }
        val days =
            request.targets.map { it.dayEpochDay }.ifEmpty {
                (0 until request.days).map { request.startDayEpochDay + it }
            }
        val usable = recipes.filter { it.archivedAtEpochMs == null }

        // 1. Filter (hard rules; rejections counted per rule for the report).
        val rejections = mutableMapOf<String, Int>()
        val eligible = usable.filter { passesHardRules(it, request.constraints, rejections) }

        // Candidate order: seeded shuffle — equal scores resolve by seed-stable
        // permutation, so a new seed re-rolls the week without new content.
        val shuffled =
            if (eligible.size > 1) {
                val copy = eligible.toMutableList()
                // Fisher–Yates with the seeded PRNG — deterministic for the seed.
                for (i in copy.size - 1 downTo 1) {
                    val j = random.nextInt(i + 1)
                    val tmp = copy[i]
                    copy[i] = copy[j]
                    copy[j] = tmp
                }
                copy
            } else {
                eligible
            }
        val bySlot: Map<String, List<Recipe>> =
            request.slots.associateWith { slot -> shuffled.filter { it.slots.contains(slot) } }

        val usageCount = mutableMapOf<RecipeId, Int>()
        val chosenGroceries = mutableSetOf<String>()
        val slotsOut = mutableListOf<SlotDraft>()
        val cookEvents = mutableListOf<Triple<Long, String, RecipeId>>() // (day, slot, recipe)

        // 2 + 3. Greedy deal with overlap + variety terms.
        for (day in days) {
            val dayTargets = targetsByDay[day] ?: continue
            for (slot in request.slots) {
                val candidates = bySlot[slot].orEmpty()
                if (candidates.isEmpty()) {
                    slotsOut += unfillable(day, slot, unfillableReason(request.constraints, rejections))
                    continue
                }
                val slotKcal = slotBudgetKcal(dayTargets, request.slots, slot)
                val best =
                    candidates
                        .map { it to score(it, slotKcal, dayTargets, request, usageCount, chosenGroceries, slot) }
                        .maxWithOrNull(
                            compareBy<Pair<Recipe, Double>> { it.second }
                                .thenByDescending { it.first.id },
                        )
                if (best == null || best.second <= Double.NEGATIVE_INFINITY) {
                    slotsOut += unfillable(day, slot, unfillableReason(request.constraints, rejections))
                    continue
                }
                val recipe = best.first
                usageCount[recipe.id] = (usageCount[recipe.id] ?: 0) + 1
                recipe.ingredients.forEach { chosenGroceries.add(it.groceryItemId) }
                slotsOut +=
                    SlotDraft(
                        dayEpochDay = day,
                        mealSlot = slot,
                        recipeId = recipe.id,
                        recipeVersion = recipe.version,
                        servings = request.settings.defaultServings,
                    )
            }
        }

        // 3b. Pairwise refinement: bounded same-slot-class swaps that raise the
        // combined day fit (overlap + variety recomputed per accepted swap).
        var swapBudget = ConstantsRegistry.PLANNER_PAIRWISE_SWAP_BUDGET
        var improved = true
        while (improved && swapBudget > 0) {
            improved = false
            outer@ for (i in slotsOut.indices) {
                for (j in slotsOut.indices) {
                    if (swapBudget <= 0) break@outer
                    val a = slotsOut[i]
                    val b = slotsOut[j]
                    if (i >= j || a.recipeId == null || b.recipeId == null) continue
                    if (a.mealSlot != b.mealSlot || a.dayEpochDay == b.dayEpochDay) continue
                    val recipeA = usable.firstOrNull { it.id == a.recipeId } ?: continue
                    val recipeB = usable.firstOrNull { it.id == b.recipeId } ?: continue
                    val targetsA = targetsByDay[a.dayEpochDay] ?: continue
                    val targetsB = targetsByDay[b.dayEpochDay] ?: continue
                    val before =
                        fitScore(recipeA, slotBudgetKcal(targetsA, request.slots, a.mealSlot)) +
                            fitScore(recipeB, slotBudgetKcal(targetsB, request.slots, b.mealSlot))
                    val after =
                        fitScore(recipeB, slotBudgetKcal(targetsA, request.slots, a.mealSlot)) +
                            fitScore(recipeA, slotBudgetKcal(targetsB, request.slots, b.mealSlot))
                    swapBudget--
                    if (after > before + SWAP_EPSILON) {
                        slotsOut[i] = a.copy(recipeId = recipeB.id, recipeVersion = recipeB.version)
                        slotsOut[j] = b.copy(recipeId = recipeA.id, recipeVersion = recipeA.version)
                        improved = true
                    }
                }
            }
        }

        // 4. Leftover slotting: a recipe the solver repeated becomes one cook
        // event + one leftover child (charged to the cook day), within the cap.
        val leftoversApplied = applyLeftovers(slotsOut, usable, request)

        // 5. Report.
        val fits =
            days.map { day ->
                val daySlots = slotsOut.filter { it.dayEpochDay == day && it.recipeId != null }
                fitBadge(daySlots, usable, targetsByDay[day] ?: DayTargets(day, 0.0))
            }
        val filled = slotsOut.count { it.recipeId != null }
        val distinct = slotsOut.mapNotNull { it.recipeId }.distinct().size
        val plannedRecipes = slotsOut.mapNotNull { draft -> usable.firstOrNull { it.id == draft.recipeId } }
        val groceryUses = plannedRecipes.flatMap { it.ingredients.map { ing -> ing.groceryItemId } }
        val shared = groceryUses.groupingBy { it }.eachCount().count { it.value > 1 }
        val overlapScore =
            if (plannedRecipes.isEmpty()) {
                0.0
            } else {
                shared.toDouble() / plannedRecipes.distinctBy { it.id }.size.coerceAtLeast(1)
            }
        val conflict =
            request.settings.varietyBias > 0.0 &&
                request.settings.overlapWeight > 0.0 &&
                distinct > 0 &&
                overlapScore > 0.5 &&
                distinct < days.size
        val hardest =
            rejections.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { (rule, count) -> "$rule (blocked $count recipes)" }
        val report =
            Report(
                filledSlots = filled,
                unfillableSlots = slotsOut.size - filled,
                distinctRecipes = distinct,
                sharedIngredients = shared,
                overlapScore = round3(overlapScore),
                cookEvents = leftoversApplied.cookEvents,
                leftoverServings = leftoversApplied.leftoverServings,
                objectivesConflict = conflict,
                hardestRules = hardest,
            )
        return GeneratedPlan(slots = slotsOut, fits = fits, report = report)
    }

    /**
     * Top-3 swap suggestions for one planned slot — the picks that best restore
     * the day's target fit (F03 §3 "Swap with rebalancing"). Deterministic,
     * descending by fit; allergens hard-block (F03 §9: swap suggestions too).
     */
    public fun swapSuggestions(
        recipes: List<Recipe>,
        slot: SlotDraft,
        dayTargets: DayTargets,
        slotsOfDay: List<String>,
        constraints: Constraints,
        excludeRecipeId: RecipeId? = slot.recipeId,
        limit: Int = 3,
    ): List<Recipe> {
        val slotKcal = slotBudgetKcal(dayTargets, slotsOfDay, slot.mealSlot)
        return recipes
            .asSequence()
            .filter { it.archivedAtEpochMs == null && it.slots.contains(slot.mealSlot) && it.id != excludeRecipeId }
            .filter { passesHardRules(it, constraints, mutableMapOf()) }
            .map { it to fitScore(it, slotKcal) }
            .sortedWith(compareByDescending<Pair<Recipe, Double>> { it.second }.thenBy { it.first.id })
            .take(limit)
            .map { it.first }
            .toList()
    }

    // --- Slot state machine (R-B1; F03 §3 "planned → confirmed | swapped | skipped | replaced") ---

    /**
     * Transition legality. [PLANNED] is the only mutable state; [CONFIRMED],
     * [SKIPPED] and [REPLACED] are terminal (F03 §7: F03 owns the slot "until
     * confirmed or replaced"). [SWAPPED] is the retired record of a swap — a
     * swap issues planned → swapped on the old row and a fresh planned
     * successor (never an in-place recipe rewrite), so history stays honest.
     */
    public fun canTransition(
        from: PlannedSlotState,
        to: PlannedSlotState,
    ): Boolean =
        when (from) {
            PlannedSlotState.PLANNED ->
                to == PlannedSlotState.CONFIRMED ||
                    to == PlannedSlotState.SWAPPED ||
                    to == PlannedSlotState.SKIPPED ||
                    to == PlannedSlotState.REPLACED
            // Terminal states: the honest history is append-only.
            PlannedSlotState.CONFIRMED,
            PlannedSlotState.SWAPPED,
            PlannedSlotState.SKIPPED,
            PlannedSlotState.REPLACED,
            -> false
        }

    /** Guarded transition — engines stay total; repositories branch on the outcome. */
    public fun transition(
        slot: PlannedSlot,
        to: PlannedSlotState,
    ): PlannedSlot? =
        if (canTransition(slot.state, to)) {
            slot.copy(state = to)
        } else {
            null
        }

    // --- Day projection (R-B1: planned vs logged distinction; R-B4 one definition) ---

    /**
     * The planned-day totals a day record renders against logged intake:
     * sums slot nutrition × servings over [PlannedSlotState.PLANNED] +
     * [PlannedSlotState.CONFIRMED] slots (the plan's full claim for the day).
     * SWAPPED rows are retired (their successor carries the claim); SKIPPED
     * and REPLACED contribute nothing (a replaced meal's nutrition is the F02
     * entry's, which surfaces as logged intake — never double-counted).
     * Leftover children carry their own per-serving macros, so nutrition
     * follows EATING slots while ingredient cost follows the cook slot.
     */
    public fun plannedDayTotals(slots: List<PlannedSlot>): NutritionTotals =
        slots
            .filter { it.recipeId != null || it.itemJson != null }
            .filter { it.state == PlannedSlotState.PLANNED || it.state == PlannedSlotState.CONFIRMED }
            .fold(NutritionTotals()) { acc, slot ->
                val n = slot.servings
                NutritionTotals(
                    kcal = acc.kcal + (slot.kcalPerServing ?: 0.0) * n,
                    proteinG = acc.proteinG + (slot.proteinGPerServing ?: 0.0) * n,
                    carbG = acc.carbG + (slot.carbGPerServing ?: 0.0) * n,
                    fatG = acc.fatG + (slot.fatGPerServing ?: 0.0) * n,
                    fiberG = acc.fiberG + (slot.fiberGPerServing ?: 0.0) * n,
                    slotCount = acc.slotCount + 1,
                )
            }

    @Serializable
    public data class NutritionTotals(
        public val kcal: Double = 0.0,
        public val proteinG: Double = 0.0,
        public val carbG: Double = 0.0,
        public val fatG: Double = 0.0,
        public val fiberG: Double = 0.0,
        public val slotCount: Int = 0,
    )

    /** The fit badge for one day from its dealt slots (R-S7 ±5 % kcal). */
    public fun fitBadge(
        drafts: List<SlotDraft>,
        recipes: List<Recipe>,
        targets: DayTargets,
    ): FitBadge {
        val byId = recipes.associateBy { it.id }
        var kcal = 0.0
        var protein = 0.0
        var fiber = 0.0
        drafts.forEach { draft ->
            val recipe = byId[draft.recipeId] ?: return@forEach
            val n = draft.servings
            kcal += recipe.nutrition.kcal * n
            protein += recipe.nutrition.proteinG * n
            fiber += recipe.nutrition.fiberG * n
        }
        return badgeFor(kcal = kcal, proteinG = protein, fiberG = fiber, targets = targets)
    }

    /** Pure badge math over known totals (shared by generation and post-swap re-render). */
    public fun badgeFor(
        kcal: Double,
        proteinG: Double,
        fiberG: Double,
        targets: DayTargets,
    ): FitBadge {
        val deltaPct =
            if (targets.kcal <= 0.0) {
                0.0
            } else {
                (kcal - targets.kcal) / targets.kcal * 100.0
            }
        return FitBadge(
            targetKcal = targets.kcal,
            plannedKcal = round1(kcal),
            kcalDeltaPct = round1(deltaPct),
            kcalWithinTolerance = kotlin.math.abs(deltaPct) <= ConstantsRegistry.PLANNER_FIT_TOLERANCE_PCT,
            proteinTargetG = targets.proteinG?.let { round1(it) },
            proteinDeltaG = targets.proteinG?.let { round1(proteinG - it) },
            fiberTargetG = targets.fiberG?.let { round1(it) },
            fiberDeltaG = targets.fiberG?.let { round1(fiberG - it) },
        )
    }

    // --- Internals ---------------------------------------------------------------

    private const val SWAP_EPSILON = 1e-9

    private fun unfillable(
        day: Long,
        slot: String,
        reason: String,
    ): SlotDraft = SlotDraft(dayEpochDay = day, mealSlot = slot, recipeId = null, unfillableReason = reason)

    /**
     * Hard rules (F03 §3 step 1 + §9). Matching is word-normalized "contains"
     * over the recipe name, ingredient names, and facet tags; [rejections]
     * counts which rule bound hardest (the explainer's "hardest rules" line).
     */
    private fun passesHardRules(
        recipe: Recipe,
        constraints: Constraints,
        rejections: MutableMap<String, Int>,
    ): Boolean {
        val haystack = ruleHaystack(recipe)

        fun reject(rule: String): Boolean {
            rejections[rule] = (rejections[rule] ?: 0) + 1
            return false
        }

        for (allergy in constraints.allergies) {
            if (matchesRule(haystack, allergy)) return reject("allergy: $allergy")
        }
        for (exclusion in constraints.exclusions) {
            if (matchesRule(haystack, exclusion)) return reject("exclusion: $exclusion")
        }
        for (required in constraints.requireTags) {
            if (!recipe.tags.contains(required)) return reject("requires: $required")
        }
        for (forbidden in constraints.forbidTags) {
            if (recipe.tags.contains(forbidden)) return reject("forbidden: $forbidden")
        }
        if (constraints.lowFodmap &&
            recipe.fodmapTags.any { it != app.wlo.core.model.FodmapTags.UNKNOWN }
        ) {
            return reject("low-FODMAP")
        }
        if (constraints.minKcalPerSlot > 0.0 && recipe.nutrition.kcal < constraints.minKcalPerSlot) {
            return reject("kcal floor ${constraints.minKcalPerSlot}")
        }
        return true
    }

    private fun ruleHaystack(recipe: Recipe): String =
        buildString {
            append(recipe.name.lowercase())
            recipe.tags.forEach {
                append(' ')
                append(it.lowercase())
            }
            recipe.ingredients.forEach {
                append(' ')
                append(it.name.lowercase())
            }
        }

    /** Word-boundary match with a small big-8 allergen alias table (F03 §9). */
    private fun matchesRule(
        haystack: String,
        rule: String,
    ): Boolean {
        val needle = rule.trim().lowercase()
        if (needle.isEmpty()) return false
        if (haystack.containsWord(needle)) return true
        return ALLERGEN_ALIASES[needle].orEmpty().any { haystack.containsWord(it) }
    }

    private fun String.containsWord(word: String): Boolean {
        var index = indexOf(word)
        while (index >= 0) {
            val beforeOk = index == 0 || !this[index - 1].isLetter()
            val after = index + word.length
            val afterOk = after >= this.length || !this[after].isLetter()
            if (beforeOk && afterOk) return true
            index = indexOf(word, index + 1)
        }
        return false
    }

    private val ALLERGEN_ALIASES: Map<String, List<String>> =
        mapOf(
            "dairy" to listOf("milk", "cheese", "yogurt", "yoghurt", "butter", "cream", "feta", "parmesan", "mozzarella", "ghee", "whey"),
            "gluten" to listOf("wheat", "bread", "pasta", "flour", "orzo", "couscous", "barley", "rye", "tortilla", "noodles"),
            "egg" to listOf("eggs", "mayonnaise"),
            "nuts" to listOf("almond", "walnut", "cashew", "peanut", "pecan", "hazelnut", "pistachio", "tahini"),
            "soy" to listOf("tofu", "soybeans", "edamame", "miso", "tempeh", "soy sauce", "tamari"),
            "fish" to listOf("salmon", "tuna", "cod", "trout", "anchovy", "sardine", "mackerel"),
            "shellfish" to listOf("shrimp", "prawn", "crab", "lobster", "clam", "mussel", "scallop"),
            "sesame" to listOf("tahini", "sesame"),
        )

    /** Slot kcal budget: the day budget renormalized over the slots actually planned. */
    private fun slotBudgetKcal(
        targets: DayTargets,
        plannedSlots: List<String>,
        slot: String,
    ): Double {
        val share = ConstantsRegistry.PLANNER_SLOT_SHARES[slot] ?: (1.0 / plannedSlots.size)
        val totalShare = plannedSlots.sumOf { ConstantsRegistry.PLANNER_SLOT_SHARES[it] ?: (1.0 / plannedSlots.size) }
        return if (totalShare <= 0.0) targets.kcal / plannedSlots.size else targets.kcal * share / totalShare
    }

    /** Combined score: macro fit + overlap bonus − variety/dislike penalties. */
    private fun score(
        recipe: Recipe,
        slotKcal: Double,
        dayTargets: DayTargets,
        request: PlanRequest,
        usageCount: Map<RecipeId, Int>,
        chosenGroceries: Set<String>,
        slot: String,
    ): Double {
        if (!recipe.slots.contains(slot)) return Double.NEGATIVE_INFINITY
        var value = fitScore(recipe, slotKcal)
        val proteinTarget = dayTargets.proteinG
        if (proteinTarget != null && proteinTarget > 0) {
            val share = slotShareFraction(request.slots, slot)
            value += 0.5 * gapScore(recipe.nutrition.proteinG, proteinTarget * share)
        }
        val fiberTarget = dayTargets.fiberG
        if (fiberTarget != null && fiberTarget > 0) {
            val share = slotShareFraction(request.slots, slot)
            value += 0.5 * gapScore(recipe.nutrition.fiberG, fiberTarget * share)
        }
        if (chosenGroceries.isNotEmpty() && request.settings.overlapWeight > 0.0) {
            val overlap = recipe.ingredients.count { chosenGroceries.contains(it.groceryItemId) }
            value += request.settings.overlapWeight * overlap / (recipe.ingredients.size.coerceAtLeast(1) * 2.0)
        }
        val used = usageCount[recipe.id] ?: 0
        if (used > 0) value -= request.settings.varietyBias * used
        if (request.constraints.dislikes.any { ruleHaystack(recipe).containsWord(it.lowercase()) }) {
            value -= DISLIKE_PENALTY
        }
        return value
    }

    /** Calorie distance term: 1.0 at zero gap, decaying linearly out to ±50 %. */
    private fun fitScore(
        recipe: Recipe,
        slotKcal: Double,
    ): Double {
        if (slotKcal <= 0.0) return 0.0
        val gapPct = kotlin.math.abs(recipe.nutrition.kcal - slotKcal) / slotKcal
        return (1.0 - gapPct * 2.0).coerceAtLeast(-1.0)
    }

    /** Protein/fiber gap term against the slot's share of the day target. */
    private fun gapScore(
        actual: Double,
        target: Double,
    ): Double {
        if (target <= 0.0) return 0.0
        return (1.0 - kotlin.math.abs(actual - target) / target).coerceAtLeast(-1.0)
    }

    private fun slotShareFraction(
        plannedSlots: List<String>,
        slot: String,
    ): Double {
        val share = ConstantsRegistry.PLANNER_SLOT_SHARES[slot] ?: (1.0 / plannedSlots.size)
        val total = plannedSlots.sumOf { ConstantsRegistry.PLANNER_SLOT_SHARES[it] ?: (1.0 / plannedSlots.size) }
        return if (total <= 0.0) 1.0 / plannedSlots.size else share / total
    }

    private data class LeftoverOutcome(
        val cookEvents: Int,
        val leftoverServings: Double,
    )

    /**
     * Cook-once-eat-twice (F03 §3): the FIRST occurrence of a recipe the
     * solver repeated later in the week becomes the cook event (batch =
     * both days' servings); the later occurrence becomes a leftover child
     * (parent = the cook slot) — ingredient weight charged to the cook day
     * only, nutrition still follows the eating day.
     */
    private fun applyLeftovers(
        slotsOut: MutableList<SlotDraft>,
        usable: List<Recipe>,
        request: PlanRequest,
    ): LeftoverOutcome {
        if (!request.settings.leftoversEnabled) return LeftoverOutcome(0, 0.0)
        var cookEvents = 0
        var leftoverServings = 0.0
        val byId = usable.associateBy { it.id }
        val byRecipe = slotsOut.filter { it.recipeId != null }.groupBy { it.recipeId!! }
        byRecipe.forEach { (recipeId, occurrences) ->
            if (cookEvents >= request.settings.maxCookEventsPerWeek) return@forEach
            if (occurrences.size < 2) return@forEach
            val sorted = occurrences.sortedBy { it.dayEpochDay }
            val cook = sorted.first()
            val leftover = sorted.firstOrNull { it.dayEpochDay > cook.dayEpochDay } ?: return@forEach
            byId[recipeId] ?: return@forEach
            val cookIndex = slotsOut.indexOf(cook)
            val leftoverIndex = slotsOut.indexOf(leftover)
            if (cookIndex < 0 || leftoverIndex < 0) return@forEach
            slotsOut[cookIndex] =
                cook.copy(
                    isCookEvent = true,
                    // Servings stay per-EATING (nutrition never double-counts);
                    // the batch only drives ingredient expansion on the cook day.
                    batchServings = cook.servings + leftover.servings,
                )
            slotsOut[leftoverIndex] =
                leftover.copy(
                    parentCookDay = cook.dayEpochDay,
                )
            cookEvents++
            leftoverServings += leftover.servings
        }
        return LeftoverOutcome(cookEvents, leftoverServings)
    }

    private fun unfillableReason(
        constraints: Constraints,
        rejections: Map<String, Int>,
    ): String {
        val hardest = rejections.entries.maxByOrNull { it.value }?.key
        return when {
            hardest != null -> "nothing in the library satisfies: $hardest — add a recipe, or relax one rule"
            constraints.requireTags.isNotEmpty() -> "no recipe carries ${constraints.requireTags.joinToString()}"
            else -> "library too small for this slot — add or import a recipe"
        }
    }

    private const val DISLIKE_PENALTY = 2.0

    /** Recipes tagged batch are preferred leftovers candidates; kept as a tag vocabulary constant. */
    public const val BATCH_TAG: String = "batch"

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

    private fun round3(value: Double): Double = kotlin.math.round(value * 1000.0) / 1000.0
}
