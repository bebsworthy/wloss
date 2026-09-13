package app.wlo.feature.f03.planning.state

import app.wlo.core.model.DerivedValue
import app.wlo.core.model.PlannedSlotState

/*
 * The plan surface's render shapes (F03 §4). Every plan-claimed number stays a
 * [DerivedValue] end-to-end so the screen renders it only through the
 * provenance components (D6); raw doubles appear solely as recipe-constant
 * per-serving receipts (the recipe row itself is the provenance).
 */

/** One slot row in the week grid (a `PlannedSlot` + its render words). */
public data class PlannedSlotUi(
    public val id: String,
    public val dayEpochDay: Long,
    public val mealSlot: String,
    public val recipeId: String?,
    public val recipeName: String?,
    public val state: PlannedSlotState,
    /** Recipe-constant per-serving kcal (the receipt under the name). */
    public val kcalPerServing: Double?,
    public val proteinGPerServing: Double?,
    public val servings: Double,
    public val isCookEvent: Boolean,
    public val isLeftover: Boolean,
    public val batchServings: Double?,
    /** Null = a filled slot; otherwise the honest "add anything" reason. */
    public val unfillableReason: String?,
) {
    public val planned: Boolean
        get() = state == PlannedSlotState.PLANNED

    public val resolved: Boolean
        get() =
            state == PlannedSlotState.CONFIRMED ||
                state == PlannedSlotState.SKIPPED ||
                state == PlannedSlotState.REPLACED
}

/** The R-S7 day-fit badge payload (the gap, never a verdict). */
public data class FitBadgeUi(
    public val kcalDeltaPct: Double,
    public val proteinDeltaG: Double?,
    public val withinTolerance: Boolean,
)

/** One day section: header (badge + rollup) + its slot rows. */
public data class PlanDayUi(
    public val dayEpochDay: Long,
    public val label: String,
    public val isToday: Boolean,
    public val isPast: Boolean,
    public val slots: List<PlannedSlotUi>,
    /** The plan's kcal claim for the day (planned + confirmed fold), chipped. */
    public val plannedKcal: DerivedValue<Double>?,
    public val budgetKcal: DerivedValue<Double>?,
    public val fit: FitBadgeUi?,
    public val openCount: Int,
) {
    public val slotCount: Int
        get() = slots.size
}

/** The "why this plan" panel (F03 §8 [v1]: visible reasoning, honest). */
public data class WhyPlanUi(
    public val filledSlots: Int,
    public val unfillableSlots: Int,
    public val distinctRecipes: Int,
    public val sharedIngredients: Int,
    public val cookEvents: Int,
    public val leftoverServings: Double,
    public val objectivesConflict: Boolean,
    public val hardestRules: List<String>,
)

/** The adherence mini-view (R-B4; F11 consumes read-only later). */
public data class AdherenceUi(
    public val meaningful: Boolean,
    public val gateReason: String?,
    public val planCoveragePct: Double?,
    public val energyFidelityKcal: Double?,
    public val cells: List<AdherenceCellUi>,
)

public data class AdherenceCellUi(
    public val label: String,
    public val confirmed: Boolean,
    public val skipped: Boolean,
)

/** One diary row offered to "ate something else instead" (R-B1 replace link). */
public data class DiaryCandidateUi(
    public val entryId: String,
    public val label: String,
)

/** The recipe library row (the Recipes segment). */
public data class RecipeRowUi(
    public val id: String,
    public val name: String,
    public val kcalPerServing: Double,
    public val slots: List<String>,
    public val tags: List<String>,
    public val sourceWord: String,
    public val version: Int,
)

/** A swap suggestion with its delta chips against the meal it would replace. */
public data class SwapSuggestionUi(
    public val recipeId: String,
    public val name: String,
    public val kcalPerServing: Double,
    public val kcalDelta: Double,
    public val proteinDeltaG: Double,
)

/** The open slot-detail sheet's payload. */
public data class SlotSheetUi(
    public val slot: PlannedSlotUi,
    public val tags: List<String>,
    public val ingredientCount: Int,
    public val nutritionBasisWord: String,
    public val diaryCandidates: List<DiaryCandidateUi>,
)

/** The open swap sheet's payload. */
public data class SwapSheetUi(
    public val slot: PlannedSlotUi,
    public val suggestions: List<SwapSuggestionUi>,
)

/** The whole plan segment's state. */
public data class PlanUiState(
    public val profileId: String?,
    public val todayEpochDay: Long,
    public val planId: String?,
    public val planVersion: Int?,
    public val weekLabel: String?,
    public val days: List<PlanDayUi>,
    public val why: WhyPlanUi?,
    public val adherence: AdherenceUi?,
    public val recipes: List<RecipeRowUi>,
    public val recipeQuery: String,
    /** Pending generation (the <30 s budget; the UI shows a quiet working note). */
    public val generating: Boolean,
    public val notice: String?,
    public val slotSheet: SlotSheetUi?,
    public val swapSheet: SwapSheetUi?,
    /** Day the grid should scroll to / highlight (deep-link focus). */
    public val focusDay: Long?,
    public val focusSlot: String?,
) {
    public val hasPlan: Boolean
        get() = planId != null && days.isNotEmpty()

    public companion object {
        public val LOADING: PlanUiState =
            PlanUiState(
                profileId = null,
                todayEpochDay = 0L,
                planId = null,
                planVersion = null,
                weekLabel = null,
                days = emptyList(),
                why = null,
                adherence = null,
                recipes = emptyList(),
                recipeQuery = "",
                generating = false,
                notice = null,
                slotSheet = null,
                swapSheet = null,
                focusDay = null,
                focusSlot = null,
            )
    }
}
