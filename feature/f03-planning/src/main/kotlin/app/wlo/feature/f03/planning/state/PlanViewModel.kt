package app.wlo.feature.f03.planning.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.AppError
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.DayView
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.GenerateWeekPlan
import app.wlo.core.data.NewRecipe
import app.wlo.core.data.PlanView
import app.wlo.core.data.PlannerRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.RecipeRepository
import app.wlo.core.engines.AdherenceMetrics
import app.wlo.core.model.DiaryEntry
import app.wlo.core.model.NutritionBasis
import app.wlo.core.model.NutritionPerServing
import app.wlo.core.model.PlannedSlot
import app.wlo.core.model.PlannedSlotState
import app.wlo.core.model.RecipeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Plan-surface intents (MVI-lite). */
public sealed interface PlanEvent {
    /** Generates the week from today (or [startDay]); a seed re-rolls the deal. */
    public data class Generate(
        val startDay: Long? = null,
        val seed: Long? = null,
    ) : PlanEvent

    /** "deal again" — same inputs, fresh seed (the shuffle button). */
    public data object DealAgain : PlanEvent

    public data class OpenSlot(
        val slotId: String,
    ) : PlanEvent

    public data object DismissSlotSheet : PlanEvent

    /** planned → confirmed (one tap; feeds adherence, F03 §1). */
    public data class Confirm(
        val slotId: String,
    ) : PlanEvent

    /** planned → skipped — the neutral, honest miss (F03 §6). */
    public data class Skip(
        val slotId: String,
    ) : PlanEvent

    /** planned → replaced, linking the F02 diary entry that owns nutrition (R-B1). */
    public data class Replace(
        val slotId: String,
        val diaryEntryId: String,
    ) : PlanEvent

    /** Opens the swap sheet: the engine's top-3 suggestions for the slot. */
    public data class RequestSwap(
        val slotId: String,
    ) : PlanEvent

    public data object DismissSwapSheet : PlanEvent

    /** Picks a suggestion; swaps are debounced so quick re-picks stay live (<100 ms re-deal). */
    public data class Swap(
        val slotId: String,
        val recipeId: String,
    ) : PlanEvent

    public data class QueryRecipes(
        val query: String,
    ) : PlanEvent

    /** Persists the R-U15 manual recipe (create, or edit → version N+1). */
    public data class SaveRecipe(
        val form: RecipeForm,
    ) : PlanEvent
}

/**
 * The R-U15 manual recipe form: per-serving macros + tags, lean but real.
 * [existingId] null = create; set = edit (the repository writes version N+1).
 */
public data class RecipeForm(
    public val existingId: String? = null,
    public val name: String = "",
    public val servingsBase: String = "2",
    public val mealSlots: Set<String> = emptySet(),
    public val kcal: String = "",
    public val proteinG: String = "",
    public val carbG: String = "",
    public val fatG: String = "",
    public val fiberG: String = "",
    public val tags: String = "",
) {
    public val valid: Boolean
        get() =
            name.isNotBlank() &&
                (servingsBase.toDoubleOrNull() ?: 0.0) > 0.0 &&
                (kcal.toDoubleOrNull() ?: 0.0) > 0.0 &&
                (proteinG.toDoubleOrNull() ?: 0.0) >= 0.0 &&
                (carbG.toDoubleOrNull() ?: 0.0) >= 0.0 &&
                (fatG.toDoubleOrNull() ?: 0.0) >= 0.0 &&
                (fiberG.toDoubleOrNull() ?: 0.0) >= 0.0 &&
                mealSlots.isNotEmpty()
}

/** One spine snapshot the renderer folds into UI state. */
private data class PlanInputs(
    val profileId: String?,
    val plan: PlanView?,
    val dayViews: List<DayView>,
    val library: List<app.wlo.core.model.Recipe>,
    val adherence: AdherenceMetrics.AdherenceReport?,
)

/**
 * The Plan surface's state holder (F03 §4): the week grid from the persisted
 * plan, per-day fit badges computed against the day projection (R-S7 ±5 %),
 * the "why this plan" panel from the engine report, the adherence mini-view,
 * the slot state machine's one-tap transitions, and the debounced swap pipe.
 * No science here — generation and transitions belong to the engine and the
 * repository; this holder fetches, formats, and routes.
 */
@OptIn(FlowPreview::class)
public class PlanViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val planner: PlannerRepository,
    private val dayProjection: DayProjectionRepository,
    private val recipes: RecipeRepository,
    private val diary: DiaryRepository,
    /** Epoch day the surface opens focused on (the deep-link "tomorrow" focus). */
    initialFocusDay: Long? = null,
    /** Slot wire name to highlight (wlo://log/planned?slot=…). */
    initialFocusSlot: String? = null,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()

    /** Wall-clock of the last generation (ms) — the <30 s acceptance budget. */
    public var lastGenerationMs: Long = 0
        private set

    private val recipeQuery = MutableStateFlow("")
    private val volatile =
        MutableStateFlow(
            VolatileUi(
                generating = false,
                notice = null,
                slotSheet = null,
                swapSheet = null,
                focusDay = initialFocusDay,
                focusSlot = initialFocusSlot,
            ),
        )
    private var profileId: String? = null

    /** The swap pipe: rapid picks collapse to the last one — the re-deal feels instant. */
    private val swapRequests = MutableStateFlow<Pair<String, String>?>(null)

    /** Renderable plan state. */
    public val uiState: StateFlow<PlanUiState> =
        combine(inputs(), recipeQuery, volatile, ::render)
            .stateIn(viewModelScope, SharingStarted.Eagerly, PlanUiState.LOADING)

    init {
        viewModelScope.launch {
            swapRequests
                .debounce(SWAP_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { pair ->
                    pair ?: return@collect
                    val result = planner.swapSlot(pair.first, pair.second, clock.now())
                    if (result is WloResult.Err) {
                        volatile.value = volatile.value.copy(notice = "that swap didn't land — nothing changed")
                    }
                }
        }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: PlanEvent) {
        when (event) {
            is PlanEvent.Generate -> generate(event.startDay, event.seed)
            PlanEvent.DealAgain -> generate(null, newSeed())
            is PlanEvent.OpenSlot -> openSlot(event.slotId)
            PlanEvent.DismissSlotSheet -> volatile.value = volatile.value.copy(slotSheet = null)
            is PlanEvent.Confirm -> transition { id -> planner.confirmSlot(id, clock.now()) }
            is PlanEvent.Skip -> transition { id -> planner.skipSlot(id, clock.now()) }
            is PlanEvent.Replace -> transition { id -> planner.replaceSlot(id, event.diaryEntryId, clock.now()) }
            is PlanEvent.RequestSwap -> requestSwap(event.slotId)
            PlanEvent.DismissSwapSheet -> volatile.value = volatile.value.copy(swapSheet = null)
            is PlanEvent.Swap -> {
                volatile.value = volatile.value.copy(swapSheet = null)
                swapRequests.value = event.slotId to event.recipeId
            }

            is PlanEvent.QueryRecipes -> recipeQuery.value = event.query
            is PlanEvent.SaveRecipe -> saveRecipe(event.form)
        }
    }

    // --- data plumbing -----------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun inputs() =
        profiles
            .observeActive()
            .flatMapLatest { profileResult ->
                val id: String? = profileResult.getOrNull()?.id
                if (id == null) {
                    flowOf(PlanInputs(null, null, emptyList(), emptyList(), null))
                } else {
                    profileId = id
                    planner.observeCurrentPlan(id).flatMapLatest { planResult ->
                        val plan: PlanView? = planResult.getOrNull()
                        val today = today()
                        val from = minOf(today, plan?.startDayEpochDay ?: today)
                        val to = maxOf(today, plan?.endDayEpochDay ?: today)
                        val views: kotlinx.coroutines.flow.Flow<List<DayView>> =
                            if (plan == null) {
                                flowOf(emptyList())
                            } else {
                                dayProjection.observeRange(id, from, to).map { result -> result.getOrNull().orEmpty() }
                            }
                        val library: kotlinx.coroutines.flow.Flow<List<app.wlo.core.model.Recipe>> =
                            recipes.observeLibrary(id).map { result -> result.getOrNull().orEmpty() }
                        val adherence: kotlinx.coroutines.flow.Flow<AdherenceMetrics.AdherenceReport?> =
                            flow { emit(if (plan == null) null else planner.adherence(id, today).getOrNull()) }
                        combine(views, library, adherence) { dayViews, recipesUi, report ->
                            PlanInputs(id, plan, dayViews, recipesUi, report)
                        }
                    }
                }
            }

    private fun render(
        inputs: PlanInputs,
        query: String,
        vol: VolatileUi,
    ): PlanUiState {
        val today = today()
        val plan = inputs.plan
        val dayViewsByDay = inputs.dayViews.associateBy { it.dayEpochDay }
        val days =
            plan
                ?.slots
                ?.groupBy { it.dayEpochDay }
                ?.toSortedMap()
                ?.map { (day, slots) ->
                    renderDay(
                        day,
                        slots.sortedWith(compareBy({ slotOrder(it.mealSlot) }, { it.createdAtEpochMs })),
                        dayViewsByDay[day],
                        today,
                    )
                }.orEmpty()

        val why =
            plan?.report?.let { report ->
                WhyPlanUi(
                    filledSlots = report.filledSlots,
                    unfillableSlots = report.unfillableSlots,
                    distinctRecipes = report.distinctRecipes,
                    sharedIngredients = report.sharedIngredients,
                    cookEvents = report.cookEvents,
                    leftoverServings = report.leftoverServings,
                    objectivesConflict = report.objectivesConflict,
                    hardestRules = report.hardestRules,
                )
            }
        val adherence = inputs.adherence?.let { report -> adherenceUi(report, plan) }
        val trimmed = query.trim()

        return PlanUiState(
            profileId = inputs.profileId,
            todayEpochDay = today,
            planId = plan?.planId,
            planVersion = plan?.version,
            weekLabel = plan?.let { weekLabelOf(it.startDayEpochDay, it.endDayEpochDay) },
            days = days,
            why = why,
            adherence = adherence,
            recipes =
                inputs.library
                    .filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) }
                    .sortedBy { it.name.lowercase() }
                    .take(RECIPE_LIST_LIMIT)
                    .map { recipe ->
                        RecipeRowUi(
                            id = recipe.id,
                            name = recipe.name,
                            kcalPerServing = recipe.nutrition.kcal,
                            slots = recipe.slots,
                            tags = recipe.tags,
                            sourceWord = sourceWord(recipe.source),
                            version = recipe.version,
                        )
                    },
            recipeQuery = query,
            generating = vol.generating,
            notice = vol.notice,
            slotSheet = vol.slotSheet?.let { sheet -> sheet.refreshed(days) },
            swapSheet = vol.swapSheet?.let { sheet -> sheet.refreshed(days) },
            focusDay = vol.focusDay ?: if (plan != null) today else null,
            focusSlot = vol.focusSlot,
        )
    }

    /** Keeps the open sheet pointed at the slot's freshest row (a swap may have landed). */
    private fun SlotSheetUi.refreshed(days: List<PlanDayUi>): SlotSheetUi? {
        val slot = days.flatMap { it.slots }.firstOrNull { it.id == slot.id } ?: return null
        return copy(slot = slot)
    }

    private fun SwapSheetUi.refreshed(days: List<PlanDayUi>): SwapSheetUi? {
        val slot = days.flatMap { it.slots }.firstOrNull { it.id == slot.id } ?: return null
        return copy(slot = slot)
    }

    private fun adherenceUi(
        report: AdherenceMetrics.AdherenceReport,
        plan: PlanView?,
    ): AdherenceUi =
        AdherenceUi(
            meaningful = report.meaningful,
            gateReason = report.gateReason,
            planCoveragePct = report.planCoveragePct,
            energyFidelityKcal = report.energyFidelityKcal,
            cells =
                plan
                    ?.slots
                    .orEmpty()
                    .groupBy { it.dayEpochDay }
                    .toSortedMap()
                    .map { (day, slots) ->
                        AdherenceCellUi(
                            label = weekdayInitial(day),
                            confirmed =
                                slots.any {
                                    it.state == PlannedSlotState.CONFIRMED || it.state == PlannedSlotState.REPLACED
                                },
                            skipped =
                                slots.isNotEmpty() &&
                                    slots.all {
                                        it.state != PlannedSlotState.PLANNED && it.state != PlannedSlotState.CONFIRMED
                                    },
                        )
                    },
        )

    private fun renderDay(
        day: Long,
        slots: List<PlannedSlot>,
        view: DayView?,
        today: Long,
    ): PlanDayUi {
        val budget = view?.budgetKcal
        val planned = view?.plannedKcal
        val fitWarranted = budget != null && planned != null && budget.value > 0.0 && view.plannedSlotCount > 0
        val fit =
            if (fitWarranted) {
                val deltaPct = (planned.value - budget.value) / budget.value * 100.0
                val plannedProtein = view.plannedProteinG
                val proteinTarget = view.proteinG
                val proteinDelta =
                    if (plannedProtein != null && proteinTarget != null && proteinTarget.value > 0.0) {
                        plannedProtein.value - proteinTarget.value
                    } else {
                        null
                    }
                FitBadgeUi(
                    kcalDeltaPct = round1(deltaPct),
                    proteinDeltaG = proteinDelta?.let(::roundHalfUp),
                    withinTolerance = kotlin.math.abs(deltaPct) <= KCAL_TOLERANCE_PCT,
                )
            } else {
                null
            }
        return PlanDayUi(
            dayEpochDay = day,
            label = dayLabel(day),
            isToday = day == today,
            isPast = day < today,
            slots = slots.map { it.toUi() },
            plannedKcal = planned,
            budgetKcal = budget,
            fit = fit,
            openCount = slots.count { it.state == PlannedSlotState.PLANNED && it.recipeId != null },
        )
    }

    private fun PlannedSlot.toUi(): PlannedSlotUi =
        PlannedSlotUi(
            id = id,
            dayEpochDay = dayEpochDay,
            mealSlot = mealSlot,
            recipeId = recipeId,
            recipeName = recipeName,
            state = state,
            kcalPerServing = kcalPerServing,
            proteinGPerServing = proteinGPerServing,
            servings = servings,
            isCookEvent = isCookEvent,
            isLeftover = parentSlotId != null,
            batchServings = batchServings,
            unfillableReason = if (recipeId == null) UNFILLABLE_REASON else null,
        )

    // --- intents -----------------------------------------------------------------

    private fun generate(
        startDay: Long?,
        seed: Long?,
    ) {
        val id = profileId ?: return
        volatile.value = volatile.value.copy(generating = true)
        viewModelScope.launch {
            val startedAt = System.nanoTime()
            val result =
                planner.generateWeek(
                    GenerateWeekPlan(
                        profileId = id,
                        startDayEpochDay = startDay ?: today(),
                        days = PLAN_DAYS,
                        mealSlots = PLAN_SLOTS,
                        seed = seed ?: newSeed(),
                    ),
                )
            lastGenerationMs = (System.nanoTime() - startedAt) / 1_000_000
            val notice =
                when (result) {
                    is WloResult.Err -> generationNotice(result)
                    is WloResult.Ok ->
                        if ((result.value.report?.unfillableSlots ?: 0) == 0) {
                            null
                        } else {
                            "part of the week stays open — your library didn't cover every slot"
                        }
                }
            volatile.value =
                volatile.value.copy(
                    generating = false,
                    notice = notice,
                    slotSheet = null,
                    swapSheet = null,
                    focusDay = startDay ?: today(),
                    focusSlot = null,
                )
        }
    }

    private fun generationNotice(result: WloResult.Err): String =
        when (result.error) {
            is AppError.InvalidInput ->
                "no plan yet — your targets don't cover this week. Finish the diet plan, then deal."
            else -> "that didn't save — nothing changed"
        }

    private fun openSlot(slotId: String) {
        val slot =
            uiState.value.days
                .flatMap { it.slots }
                .firstOrNull { it.id == slotId } ?: return
        viewModelScope.launch {
            val recipe = slot.recipeId?.let { recipes.byId(it).getOrNull() }
            val id = profileId
            val candidates =
                if (slot.state == PlannedSlotState.PLANNED && id != null) {
                    diary
                        .day(id, slot.dayEpochDay)
                        .getOrNull()
                        ?.entries
                        .orEmpty()
                        .filter { it.mealSlot.wireName == slot.mealSlot }
                        .map { DiaryCandidateUi(it.id, entryLabel(it)) }
                } else {
                    emptyList()
                }
            volatile.value =
                volatile.value.copy(
                    slotSheet =
                        SlotSheetUi(
                            slot = slot,
                            tags = recipe?.tags.orEmpty(),
                            ingredientCount = recipe?.ingredients?.size ?: 0,
                            nutritionBasisWord =
                                when (recipe?.nutritionBasis) {
                                    NutritionBasis.LABEL -> "label"
                                    NutritionBasis.DB -> "db"
                                    else -> "estimated"
                                },
                            diaryCandidates = candidates,
                        ),
                )
        }
    }

    private fun entryLabel(entry: DiaryEntry): String = formatEntry(entry.textHint, entry.kcal)

    private fun formatEntry(
        textHint: String?,
        kcal: Double,
    ): String = "${textHint ?: "logged meal"} · ${kcal.toInt()} kcal"

    private fun transition(action: suspend (String) -> WloResult<PlannedSlot>) {
        val slotId =
            volatile.value.slotSheet
                ?.slot
                ?.id
                ?: volatile.value.swapSheet
                    ?.slot
                    ?.id
                ?: return
        viewModelScope.launch {
            val result = action(slotId)
            val notice = if (result is WloResult.Err) "that didn't save — nothing changed" else null
            volatile.value = volatile.value.copy(slotSheet = null, swapSheet = null, notice = notice)
        }
    }

    private fun requestSwap(slotId: String) {
        viewModelScope.launch {
            val suggestions = planner.swapSuggestions(slotId).getOrNull().orEmpty()
            val slot =
                uiState.value.days
                    .flatMap { it.slots }
                    .firstOrNull { it.id == slotId } ?: return@launch
            if (suggestions.isEmpty()) {
                volatile.value =
                    volatile.value.copy(
                        slotSheet = null,
                        notice = "no swaps in your library for this slot yet — add a recipe and the engine will use it",
                    )
                return@launch
            }
            volatile.value =
                volatile.value.copy(
                    slotSheet = null,
                    swapSheet =
                        SwapSheetUi(
                            slot = slot,
                            suggestions =
                                suggestions.map { recipe ->
                                    SwapSuggestionUi(
                                        recipeId = recipe.id,
                                        name = recipe.name,
                                        kcalPerServing = recipe.nutrition.kcal,
                                        kcalDelta = recipe.nutrition.kcal - (slot.kcalPerServing ?: 0.0),
                                        proteinDeltaG = recipe.nutrition.proteinG - (slot.proteinGPerServing ?: 0.0),
                                    )
                                },
                        ),
                )
        }
    }

    private fun saveRecipe(form: RecipeForm) {
        val id = profileId ?: return
        if (!form.valid) {
            volatile.value = volatile.value.copy(notice = "a name, servings and the numbers make a recipe")
            return
        }
        viewModelScope.launch {
            val newRecipe =
                NewRecipe(
                    profileId = id,
                    name = form.name.trim(),
                    servingsBase = form.servingsBase.toDoubleOrNull() ?: 1.0,
                    slots = form.mealSlots.toList(),
                    tags =
                        form.tags
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() },
                    nutrition =
                        NutritionPerServing(
                            kcal = form.kcal.toDoubleOrNull() ?: 0.0,
                            proteinG = form.proteinG.toDoubleOrNull() ?: 0.0,
                            carbG = form.carbG.toDoubleOrNull() ?: 0.0,
                            fatG = form.fatG.toDoubleOrNull() ?: 0.0,
                            fiberG = form.fiberG.toDoubleOrNull() ?: 0.0,
                        ),
                    nutritionBasis = NutritionBasis.ESTIMATED,
                    source = RecipeSource.MANUAL,
                )
            val result =
                if (form.existingId == null) {
                    recipes.create(newRecipe, clock.now())
                } else {
                    recipes.edit(form.existingId, newRecipe, clock.now())
                }
            if (result is WloResult.Err) {
                volatile.value = volatile.value.copy(notice = "that didn't save — nothing changed")
            }
        }
    }

    /** Loads a recipe into the editor form (R-U15 edit path; version N+1 on save). */
    public suspend fun loadRecipeForm(recipeId: String): RecipeForm? {
        val recipe = recipes.byId(recipeId).getOrNull() ?: return null
        return RecipeForm(
            existingId = recipe.id,
            name = recipe.name,
            servingsBase = trimNum(recipe.servingsBase),
            mealSlots = recipe.slots.toSet(),
            kcal = trimNum(recipe.nutrition.kcal),
            proteinG = trimNum(recipe.nutrition.proteinG),
            carbG = trimNum(recipe.nutrition.carbG),
            fatG = trimNum(recipe.nutrition.fatG),
            fiberG = trimNum(recipe.nutrition.fiberG),
            tags = recipe.tags.joinToString(", "),
        )
    }

    /** The library row the editor was opened for (id → name for the editor header). */
    public suspend fun recipeNameFor(recipeId: String): String? = recipes.byId(recipeId).getOrNull()?.name

    // --- small helpers -----------------------------------------------------------

    private fun newSeed(): Long = clock.now().toEpochMilliseconds() xor (volatile.value.hashCode().toLong() shl 17)

    private fun today(): Long = DayBoundary.epochDay(clock.now(), zone)

    private fun dayLabel(day: Long): String {
        val date = LocalDate.fromEpochDays(day.toInt())
        val weekday =
            date.dayOfWeek.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
                .take(3)
        return "$weekday ${date.dayOfMonth}"
    }

    private fun weekdayInitial(day: Long): String =
        LocalDate
            .fromEpochDays(day.toInt())
            .dayOfWeek.name
            .take(1)

    private fun weekLabelOf(
        from: Long,
        to: Long,
    ): String {
        val a = LocalDate.fromEpochDays(from.toInt())
        val b = LocalDate.fromEpochDays(to.toInt())
        val month = { date: LocalDate ->
            date.month.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
                .take(3)
        }
        return if (a.month ==
            b.month
        ) {
            "${month(a)} ${a.dayOfMonth}–${b.dayOfMonth}"
        } else {
            "${month(a)} ${a.dayOfMonth} – ${month(b)} ${b.dayOfMonth}"
        }
    }

    private fun slotOrder(mealSlot: String): Int =
        when (mealSlot) {
            "breakfast" -> 0
            "lunch" -> 1
            "dinner" -> 2
            else -> 3
        }

    private fun sourceWord(source: app.wlo.core.model.RecipeSource): String =
        when (source) {
            app.wlo.core.model.RecipeSource.SEED -> "seed"
            app.wlo.core.model.RecipeSource.MANUAL -> "manual"
            app.wlo.core.model.RecipeSource.IMPORT -> "import"
            app.wlo.core.model.RecipeSource.AI_DRAFT -> "ai-draft"
        }

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

    private fun roundHalfUp(value: Double): Double = kotlin.math.round(value)

    private fun trimNum(value: Double): String {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        return if (rounded == kotlin.math.floor(rounded)) rounded.toInt().toString() else rounded.toString()
    }

    /** Session-only UI (sheets/notices/focus) preserved across data re-renders. */
    private data class VolatileUi(
        val generating: Boolean,
        val notice: String?,
        val slotSheet: SlotSheetUi?,
        val swapSheet: SwapSheetUi?,
        val focusDay: Long?,
        val focusSlot: String?,
    )

    public companion object {
        /** The v1 week: seven days, the three core slots (F03 §4 / R-U11). */
        public const val PLAN_DAYS: Int = 7
        public val PLAN_SLOTS: List<String> = listOf("breakfast", "lunch", "dinner")

        /** R-S7: ±5 % kcal. */
        public const val KCAL_TOLERANCE_PCT: Double = 5.0

        /** The debounce that keeps quick swap re-picks inside the live re-deal budget. */
        public const val SWAP_DEBOUNCE_MS: Long = 120L

        /** The honest "add anything" copy (F03 §4 fallback (a)) for a null-recipe slot. */
        public const val UNFILLABLE_REASON: String =
            "nothing in your library fits this slot yet — add a recipe, or relax a rule and re-deal"

        /** Recipe-list render cap (the search field filters the observed library). */
        public const val RECIPE_LIST_LIMIT: Int = 60
    }
}
