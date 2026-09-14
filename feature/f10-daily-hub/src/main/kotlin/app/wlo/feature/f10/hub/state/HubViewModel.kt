package app.wlo.feature.f10.hub.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.DayProjector
import app.wlo.core.data.DayView
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.PlannerRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.RoomWeighInRepository
import app.wlo.core.data.TargetsRepository
import app.wlo.core.data.WeighInRepository
import app.wlo.core.designsystem.ChartPoint
import app.wlo.core.documents.DietTemplateApplier
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.engines.ColdStartInput
import app.wlo.core.engines.DayModel
import app.wlo.core.engines.DayModelEngine
import app.wlo.core.engines.DayModelInput
import app.wlo.core.engines.DayPhase
import app.wlo.core.engines.ForecastBands
import app.wlo.core.engines.ForecastEngine
import app.wlo.core.engines.StreakMetrics
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.MealSlot
import app.wlo.core.model.PlannedSlot
import app.wlo.core.model.PlannedSlotState
import app.wlo.core.model.Profile
import app.wlo.core.model.Provenance
import app.wlo.core.model.UnitSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * F10 Hub state (the M2 hub, relocated into its owning module and re-rendered
 * through the Day Model engine, F10 §3): phase-aware cards in spec order, the
 * hero slot, and the diary slice (F10 renders today's diary; F02 owns it —
 * R-B1). Every number travels as [DerivedValue]; rendering goes only through
 * provenance-chip components (D6).
 */
public sealed interface HubUiState {
    /** First emissions in flight. */
    public data object Loading : HubUiState

    /** No active profile — the shell gate shows the wizard instead of this. */
    public data object Fresh : HubUiState

    public data class Ready(
        public val todayLabel: String,
        public val phase: DayPhase,
        public val dayModel: DayModel,
        /**
         * The hero's trend-first number, UNIT-FREE (owner review WLO-0030,
         * defect 14: "kg" used to be baked into a pre-formatted string so
         * numeral + unit rendered as one display-size run). [massUnit] owns
         * the symbol; the surface formats numeral and unit separately.
         */
        public val heroTrend: DerivedValue<Double>,
        public val massUnit: MassUnit,
        /** The weekly delta for the hero chip (null until gated in). */
        public val heroDelta: DerivedValue<Double>?,
        public val trendChipAvailable: Boolean,
        /** The 30-day sparkline's raw scalars + canonical trend line. */
        public val trendSamples: List<ChartPoint>,
        public val trendLine: List<ChartPoint>,
        /** "How we got here" for the trend chip (null while the gate holds). */
        public val trendExplainer: ExplainerUi?,
        public val budget: DerivedValue<Double>?,
        public val budgetExplainer: ExplainerUi?,
        public val burn: DerivedValue<String>?,
        public val burnExplainer: ExplainerUi?,
        public val diarySlice: DiarySliceUi?,
        public val diaryExplainer: ExplainerUi?,
        /** Current week M..S — one dot per day, logged vs not (the mock's row). */
        public val weekDots: List<WeekDotUi>,
        public val forecast: HubForecast?,
        /**
         * The multi-oracle streak (F11 counting, WLO-0033 wave 1): null while
         * 0 — a hidden chip, never a zero shaming (R-D14).
         */
        public val streakCount: Int? = null,
        /**
         * The next open planned meal + the meals card's kept/total count
         * (WLO-0033 wave 2); null while no open slot exists — the card only
         * renders with content (R-D14).
         */
        public val mealToday: MealTodayUi? = null,
        /** Macro pill scalars for the calories card (both sides must exist). */
        public val macroPills: List<MacroPillUi> = emptyList(),
        public val explainer: ExplainerUi? = null,
        /** User-worded action failure ("that didn't save — nothing changed"). */
        public val notice: String? = null,
    ) : HubUiState
}

/** The diary's today slice (F10 renders it; F02 owns the diary, R-B1). */
public data class DiarySliceUi(
    public val kcal: DerivedValue<Double>,
    public val entryCount: Int,
    public val slotSummary: String,
    /** Consumed macros — null while no entry today carries that macro. */
    public val proteinG: Double? = null,
    public val carbG: Double? = null,
    public val fatG: Double? = null,
)

/**
 * One macro pill of the calories card ("P 128/165"): rendered only when both
 * the consumed side and the target side exist. [colorIndex] is the data-viz
 * series slot (the mock maps P→series 4, C→series 1, F→series 2).
 */
public data class MacroPillUi(
    public val label: String,
    public val consumedG: Double,
    public val targetG: Double,
    public val colorIndex: Int,
)

/**
 * The meals card's one row — the NEXT open planned slot (WLO-0033 wave 2,
 * mock: name weight-600 + "380 kcal · planned" receipt + the one-tap CTA)
 * plus the header count ("0 of 3" / "2 of 3 confirmed").
 */
public data class MealTodayUi(
    public val slotId: String,
    public val name: String,
    public val kcalPerServing: Double?,
    /** Slots already resolved as kept (confirmed / replaced). */
    public val kept: Int,
    /** Slots still in play (planned + kept); skips shrink the count. */
    public val total: Int,
)

/**
 * One day-dot of the diary card's week row (owner review WLO-0030, defect 12:
 * the month heatmap was never in the mock — the week dots are).
 */
public data class WeekDotUi(
    public val epochDay: Long,
    public val logged: Boolean,
    public val isToday: Boolean,
    /** Days after today render as empty promise, never as gaps. */
    public val isFuture: Boolean,
)

/**
 * One forecast render + its explainer payload. The formulaVersion + inputs
 * live HERE (one tap away) — R-D11 keeps mechanism out of the chips.
 */
public data class HubForecast(
    public val bands: HubForecastBandsUi,
    public val goalWeight: DerivedValue<Double>,
    public val estimate: DerivedValue<Double>,
    public val explainer: ExplainerUi,
)

/** Neutral band geometry for the design-system cone. */
public data class HubForecastBandsUi(
    public val startWeightKg: Double,
    public val goalWeightKg: Double,
    public val startEpochDay: Long,
    public val optimisticKg: List<Double>,
    public val expectedKg: List<Double>,
    public val pessimisticKg: List<Double>,
    public val optimisticFinishEpochDay: Long?,
    public val expectedFinishEpochDay: Long?,
    public val pessimisticFinishEpochDay: Long?,
)

/** One "how we got here" sheet's content. */
public data class ExplainerUi(
    public val headline: String,
    public val rows: List<Pair<String, String>>,
    public val note: String,
)

/** Hub intents (MVI-lite): one channel in, state out. */
public sealed interface HubEvent {
    public data class ShowExplainer(
        public val explainer: ExplainerUi,
    ) : HubEvent

    public data object DismissExplainer : HubEvent
}

/** One spine snapshot: profile + today's projection + the current targets. */
private data class HubInputs(
    val profile: Profile,
    val day: DayView?,
    val targets: TargetsRecord?,
)

/** Meals header count: the terminals that count as kept (logged or eaten-instead). */
private val KEPT_SLOT_STATES: Set<PlannedSlotState> =
    setOf(PlannedSlotState.CONFIRMED, PlannedSlotState.REPLACED)

/** Meals header count: retired swaps and neutral skips leave the denominator. */
private val OUT_OF_PLAY_STATES: Set<PlannedSlotState> =
    setOf(PlannedSlotState.SWAPPED, PlannedSlotState.SKIPPED)

/**
 * The F10 Hub state holder (moved from :app in M3; F10 §1: the Hub computes
 * no science — it composes, routes, and schedules). The Day Model engine
 * decides card existence, order, and state; this holder only fetches the
 * completeness flags the rules read (weigh-in logged, the trend gate, plan
 * conditional — R-D14) and the numbers each card renders.
 */
public class HubViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val dayProjection: DayProjectionRepository,
    private val targets: TargetsRepository,
    private val weighIns: WeighInRepository,
    private val diary: DiaryRepository,
    private val planner: PlannerRepository,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private val today: Long = DayBoundary.epochDay(clock.now(), zone)
    private val now: Instant = clock.now()

    private val explainer: MutableStateFlow<ExplainerUi?> = MutableStateFlow(null)
    private val notice: MutableStateFlow<String?> = MutableStateFlow(null)

    /** MVI-lite intent entry point. */
    public fun onEvent(event: HubEvent) {
        when (event) {
            is HubEvent.ShowExplainer -> explainer.value = event.explainer
            HubEvent.DismissExplainer -> explainer.value = null
        }
    }

    /**
     * One-tap meal replay (R-B1, WLO-0033 wave 2): logs the planned recipe as
     * a diary entry and retires the slot. Success needs no manual refresh —
     * the write refreshes the day projection, whose flow re-emits and re-renders
     * the ring, the meals card, and the streak. Failure surfaces the house
     * notice (F03's user-worded pattern); nothing changed, nothing judged.
     */
    public fun onLogAsPlanned(slotId: String) {
        viewModelScope.launch {
            val outcome = planner.logAsPlanned(slotId, clock.now())
            if (outcome is WloResult.Err) notice.value = ACTION_FAILED_NOTICE
        }
    }

    /** Clears the action-failure notice. */
    public fun dismissNotice() {
        notice.value = null
    }

    /** Renderable hub state. */
    public val uiState: StateFlow<HubUiState> =
        combine(inputs(), explainer, notice, ::withVolatile).stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            HubUiState.Loading,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun inputs(): Flow<HubUiState> =
        profiles
            .observeActive()
            .flatMapLatest { profileResult -> spineInputs(profileResult.getOrNull()) }
            .map { inputs -> render(inputs) }

    private fun spineInputs(profile: Profile?): Flow<HubInputs?> =
        if (profile == null || profile.archivedAt != null) {
            flowOf(null)
        } else {
            combine(
                dayProjection.observeDay(profile.id, today),
                targets.observeCurrent(profile.id),
            ) { dayResult, targetsResult -> HubInputs(profile, dayResult.getOrNull(), targetsResult.getOrNull()) }
        }

    private fun withVolatile(
        ready: HubUiState,
        explainerUi: ExplainerUi?,
        notice: String?,
    ): HubUiState = if (ready is HubUiState.Ready) ready.copy(explainer = explainerUi, notice = notice) else ready

    private suspend fun render(inputs: HubInputs?): HubUiState {
        if (inputs == null) return HubUiState.Fresh
        val (profile, day, current) = inputs
        val unit = massUnitFor(profile.unitPreference)

        // THE single trend source (WLO-0030 defect 9): the same repository
        // door F06's default view reads — never the persisted projection
        // snapshot, never a differently-windowed recompute.
        val trend = weighIns.currentTrend(profile.id, today).getOrNull()
        val heroTrend =
            trend?.current
                ?: DerivedValue(
                    profile.startWeightKg,
                    Provenance.Measured(at = profile.createdAt, instrument = "start-weight entry"),
                )

        // The F10 §4 gate (≥3 weigh-ins in the trailing 7 days) opens the
        // delta chip + the sparkline's trend line.
        val gateOpen =
            trend
                ?.samples
                .orEmpty()
                .count { it.epochDay > today - TREND_GATE_WINDOW_DAYS } >= TREND_GATE_MIN_POINTS
        val delta = if (gateOpen) trend?.delta7 else null
        val trendSamples =
            trend
                ?.samples
                .orEmpty()
                .map { ChartPoint(it.epochDay, it.weightKg) }
        val trendLine =
            if (gateOpen) {
                trend
                    ?.series
                    ?.points
                    .orEmpty()
                    .map { ChartPoint(it.epochDay, it.trendKg.value) }
            } else {
                emptyList()
            }

        // The calories card's budget (numeric — the ring's fill and the
        // "of N kcal" line); the explainer formats it for its sheet row.
        val budget = day?.budgetKcal
        val burn =
            day?.burnKcal?.takeIf { it.value > 0.0 }?.let { DerivedValue(formatKcal(it.value), it.provenance) }
        val forecast = current?.let { coldStartForecast(profile, it, heroTrend.value, day?.budgetKcal?.value, unit) }

        val diarySlice = renderDiarySlice(profile.id)

        // One range read feeds BOTH the week dots and the streak (the same
        // door week dots always used — the day projection range, A.3).
        val dayViews = dayProjection.range(profile.id, today - STREAK_WINDOW_DAYS, today).getOrNull().orEmpty()
        val weekDots = renderWeekDots(dayViews)
        val streakCount = renderStreak(dayViews)
        val macroPills = renderMacroPills(day, diarySlice)

        // The Day Model rules (F10 §3). Completeness inputs the features own:
        // the weigh-in flag, the F10 §4 trend gate, and (since M5) the F03
        // plan flags — content-rendered, absence is silent (R-D14).
        val localTime = now.toLocalDateTime(zone).time
        val planFlags = planFlags(profile.id)
        val dayModel =
            DayModelEngine.resolve(
                DayModelInput(
                    minutesOfDay = localTime.hour * 60 + localTime.minute,
                    weighInLogged = dayWeighInCount(profile.id) > 0,
                    trendAvailable = delta != null,
                    hasOpenPlannedMeal = (planFlags.openToday ?: 0) > 0,
                    workoutDueToday = false,
                    checkInDueToday = false,
                    isPlanner = planFlags.isPlanner,
                    planTomorrowPending = planFlags.tomorrowPending,
                ),
            )

        return HubUiState.Ready(
            todayLabel = weekdayLabel(today),
            phase = dayModel.phase,
            dayModel = dayModel,
            heroTrend = heroTrend,
            massUnit = unit,
            heroDelta = delta,
            trendChipAvailable = delta != null,
            trendSamples = trendSamples,
            trendLine = trendLine,
            trendExplainer =
                if (trend?.current != null) {
                    trendExplainer()
                } else {
                    null
                },
            budget = budget,
            budgetExplainer = budget?.let { budgetExplainer(it) },
            burn = burn,
            burnExplainer = burn?.let { burnExplainer(it) },
            diarySlice = diarySlice,
            diaryExplainer = diarySlice?.let { diaryExplainer(it) },
            weekDots = weekDots,
            forecast = forecast,
            streakCount = streakCount,
            mealToday = planFlags.mealToday,
            macroPills = macroPills,
        )
    }

    /**
     * The F03 completeness flags (R-B1/R-D14): planner-hood, the open slots,
     * and — WLO-0033 wave 2 — the renderable "next open meal" row + the
     * header count.
     *
     * Header-count rule (mock "0 of 3" / "2 of 3 confirmed"): today's slots
     * with a recipe, SWAPPED rows excluded (retired history — the successor
     * carries the claim, the same semantics AdherenceMetrics uses) and
     * SKIPPED rows excluded (mock pin 7: skipping is a neutral decision that
     * shrinks the count). Kept = the CONFIRMED + REPLACED terminals — logged
     * as planned, or eaten-something-else; a skip keeps the count honest by
     * leaving the denominator instead of showing as a miss.
     *
     * Row rule: the FIRST open slot (planned, with a recipe) in meal-slot
     * declaration order (breakfast→lunch→dinner→snack→drinks, the diary's own
     * order), ties broken by creation time then id — stable and deterministic
     * regardless of insert order.
     */
    private suspend fun planFlags(profileId: String): PlanFlags {
        val plan = planner.currentPlan(profileId).getOrNull()
        if (plan == null) {
            return PlanFlags(isPlanner = false, openToday = null, tomorrowPending = false, mealToday = null)
        }
        val todaySlots = planner.slots(profileId, today, today).getOrNull().orEmpty()
        val tomorrowSlots = planner.slots(profileId, today + 1, today + 1).getOrNull().orEmpty()
        val openSlots = todaySlots.filter { it.state == PlannedSlotState.PLANNED && it.recipeId != null }
        val openToday = openSlots.size
        val total = todaySlots.count(::inPlay)
        val kept = todaySlots.count(::keptSlot)
        return PlanFlags(
            isPlanner = true,
            openToday = openToday,
            tomorrowPending = !tomorrowSlots.any { it.state == PlannedSlotState.PLANNED && it.recipeId != null },
            mealToday =
                openSlots
                    .minWithOrNull(
                        compareBy<PlannedSlot> { MealSlot.fromWireName(it.mealSlot)?.ordinal ?: Int.MAX_VALUE }
                            .thenBy { it.createdAtEpochMs }
                            .thenBy { it.id },
                    )?.let { next ->
                        MealTodayUi(
                            slotId = next.id,
                            name = mealDisplayName(next),
                            kcalPerServing = next.kcalPerServing,
                            kept = kept,
                            total = total,
                        )
                    },
        )
    }

    /** Header-count "in play": has a recipe, neither retired (swapped) nor skipped. */
    private fun inPlay(slot: PlannedSlot): Boolean = slot.recipeId != null && slot.state !in OUT_OF_PLAY_STATES

    /** Header-count "kept": the confirmed / replaced terminals. */
    private fun keptSlot(slot: PlannedSlot): Boolean = slot.state in KEPT_SLOT_STATES

    /** The row's display name: the denormalized recipe name, else the meal word. */
    private fun mealDisplayName(slot: PlannedSlot): String {
        slot.recipeName?.let { return it }
        val slotWord = MealSlot.fromWireName(slot.mealSlot)?.let { slotLabel(it) }
        return slotWord?.replaceFirstChar { it.uppercase() } ?: "Planned meal"
    }

    private data class PlanFlags(
        val isPlanner: Boolean,
        val openToday: Int?,
        val tomorrowPending: Boolean,
        val mealToday: MealTodayUi?,
    )

    // --- diary slice + week dots (F10 renders today; F02 owns the diary, R-B1) ---

    private suspend fun renderDiarySlice(profileId: String): DiarySliceUi? {
        val day = diary.day(profileId, today).getOrNull() ?: return null
        if (day.entries.isEmpty()) return null
        val summary =
            day.slots.entries.joinToString(" · ") { (slot, entries) ->
                "${slotLabel(slot)} ${entries.size}"
            }
        return DiarySliceUi(
            kcal = DerivedValue(day.totals.kcal, dayProvenance(day.entries.size)),
            entryCount = day.entries.size,
            slotSummary = summary,
            proteinG = day.totals.proteinG,
            carbG = day.totals.carbG,
            fatG = day.totals.fatG,
        )
    }

    /**
     * The streak chip's count (WLO-0033 wave 2): the multi-oracle run over the
     * same day-projection range the week dots read — a day counts when it has
     * a weigh-in (trend scalar) OR logged food (intake scalar) OR a workout
     * (burn > 0), F11's counting rule. Null (chip hidden) when 0.
     */
    private fun renderStreak(views: List<DayView>): Int? {
        val countedDays =
            views.mapNotNullTo(mutableSetOf()) { view ->
                val counted =
                    view.trendWeightKg != null ||
                        view.intakeKcal != null ||
                        (view.burnKcal?.value ?: 0.0) > 0.0
                if (counted) view.dayEpochDay else null
            }
        return StreakMetrics.currentStreak(countedDays, today).takeIf { it > 0 }
    }

    /**
     * The calories card's macro pills ("P 128/165"): each pill renders only
     * when BOTH sides exist — the consumed macro (null while no entry today
     * carries it) and the day's target. A day with no entries at all has
     * consumed 0 — a fact, so the pills render against 0 like the mock's
     * morning frame.
     */
    private fun renderMacroPills(
        day: DayView?,
        slice: DiarySliceUi?,
    ): List<MacroPillUi> {
        if (day == null) return emptyList()
        val pills = mutableListOf<MacroPillUi>()

        fun pill(
            label: String,
            target: DerivedValue<Double>?,
            consumed: Double?,
            colorIndex: Int,
        ) {
            target ?: return
            val consumedG = if (slice == null) 0.0 else consumed ?: return
            pills.add(MacroPillUi(label, consumedG, target.value, colorIndex))
        }
        pill("P", day.proteinG, slice?.proteinG, MACRO_SERIES_P)
        pill("C", day.carbG, slice?.carbG, MACRO_SERIES_C)
        pill("F", day.fatG, slice?.fatG, MACRO_SERIES_F)
        return pills
    }

    /**
     * The diary card's week-dots row (WLO-0030 defect 12: the mock's current
     * week M..S replaces the month heatmap here): one dot per day, filled when
     * that day has logged food. Same door the heatmap read — the day
     * projection range (A.3), a day counts as logged when its intake scalar
     * exists.
     */
    private fun renderWeekDots(views: List<DayView>): List<WeekDotUi> {
        val weekStart = today - (toLocalDate(today).dayOfWeek.isoDayNumber - 1)
        val loggedDays = views.mapNotNullTo(mutableSetOf()) { view -> view.intakeKcal?.let { view.dayEpochDay } }
        return (0 until 7).map { offset ->
            val day = weekStart + offset
            WeekDotUi(
                epochDay = day,
                logged = day in loggedDays,
                isToday = day == today,
                isFuture = day > today,
            )
        }
    }

    // --- explainers ("how we got here" — every chip opens real content) ---

    /** The hero trend chip's sheet: EWMA, α, window, inputs (defect 16). */
    private fun trendExplainer(): ExplainerUi =
        ExplainerUi(
            headline = "How we got here",
            rows =
                listOf(
                    "your weight" to "a trailing average, not one reading",
                    "method" to "EWMA (exponential moving average)",
                    "responsiveness α" to ConstantsRegistry.EWMA_ALPHA_DEFAULT.toString(),
                    "window" to "the last ${RoomWeighInRepository.TREND_WINDOW_DAYS} days",
                    "input" to "your daily lowest readings",
                    "formula" to ConstantsRegistry.EWMA_FORMULA_VERSION,
                ),
            note = "Computed on your device from your daily lowest readings.",
        )

    /** The budget chip's sheet: the adaptive targets + the formulas behind them. */
    private fun budgetExplainer(budget: DerivedValue<Double>): ExplainerUi =
        ExplainerUi(
            headline = "How we got here",
            rows =
                listOf(
                    "today's budget" to formatKcal(budget.value),
                    "source" to "your adaptive targets",
                    "BMR formula" to ConstantsRegistry.BMR_FORMULA_VERSION,
                    "energy rule" to "${ConstantsRegistry.KCAL_PER_KG_FAT.toInt()} kcal per kg",
                ),
            note = "Computed on your device from your profile and plan.",
        )

    /** The burn chip's sheet: only logged workouts, never a guess. */
    private fun burnExplainer(burn: DerivedValue<String>): ExplainerUi =
        ExplainerUi(
            headline = "How we got here",
            rows =
                listOf(
                    "estimated burn, today" to burn.value,
                    "method" to "sum of the workouts you logged today",
                    "formula" to DayProjector.PROJECTION_FORMULA_VERSION,
                ),
            note = "Only workouts you logged count — nothing is assumed.",
        )

    /** The diary kcal chip's sheet: portion math over today's entries. */
    private fun diaryExplainer(slice: DiarySliceUi): ExplainerUi =
        ExplainerUi(
            headline = "How we got here",
            rows =
                listOf(
                    "logged today" to formatKcal(slice.kcal.value),
                    "entries" to slice.entryCount.toString(),
                    "method" to "portion × per-100 g energy, summed",
                    "formula" to ConstantsRegistry.DIARY_PORTION_FORMULA_VERSION,
                ),
            note = "Corrections re-run the math; every prior version stays in the entry's history.",
        )

    // --- F06 flags ---

    private suspend fun dayWeighInCount(profileId: String): Int =
        weighIns
            .dayWeighIns(profileId, today)
            .getOrNull()
            .orEmpty()
            .size

    // --- forecast (R-A5 cold start) ---

    /** R-A5 cold-start forecast (mandatory v1): visible before any weigh-in. */
    private fun coldStartForecast(
        profile: Profile,
        record: TargetsRecord,
        startTrendKg: Double,
        plannedIntakeKcal: Double?,
        unit: MassUnit,
    ): HubForecast? {
        val goal = record.document.goal
        val startTrend = startTrendKg
        if (goal.targetWeightKg >= startTrend) return null
        val intake = plannedIntakeKcal ?: DietTemplateApplier.DEFAULT_BUDGET_KCAL
        val bands: ForecastBands =
            ForecastEngine.coldStart(
                input =
                    ColdStartInput(
                        sex = profile.sex,
                        ageYears = profile.ageAtYear(now.toLocalDateTime(zone).year),
                        heightCm = profile.heightCm,
                        startTrendKg = startTrend,
                        goalWeightKg = goal.targetWeightKg,
                        activityLevel = profile.activityLevel,
                        intakeKcal = intake,
                        startEpochDay = today,
                        startInstant = now,
                    ),
            )
        return HubForecast(
            goalWeight =
                DerivedValue(
                    goal.targetWeightKg,
                    Provenance.Measured(at = now, instrument = "goal entry"),
                ),
            bands =
                HubForecastBandsUi(
                    startWeightKg = startTrend,
                    goalWeightKg = goal.targetWeightKg,
                    startEpochDay = today,
                    optimisticKg = bands.optimistic.trajectoryKg,
                    expectedKg = bands.expected.trajectoryKg,
                    pessimisticKg = bands.pessimistic.trajectoryKg,
                    optimisticFinishEpochDay = bands.optimistic.finishEpochDay,
                    expectedFinishEpochDay = bands.expected.finishEpochDay,
                    pessimisticFinishEpochDay = bands.pessimistic.finishEpochDay,
                ),
            estimate = DerivedValue(bands.tdeeEstimateKcal, bands.provenance),
            explainer =
                ExplainerUi(
                    headline = "How we got here",
                    rows =
                        listOf(
                            "forecast model" to bands.modelVersion,
                            "burn formula" to bands.bmrVersion,
                            "energy rule" to "${ConstantsRegistry.KCAL_PER_KG_FAT.toInt()} kcal per kg",
                            "start" to unit.format(startTrend),
                            "goal" to unit.format(goal.targetWeightKg),
                            "planned intake" to formatKcal(intake),
                            "a normal day" to profile.activityLevel.wireName,
                        ),
                    note = "Computed on your device — it sharpens as you log.",
                ),
        )
    }

    // --- rendering helpers ---

    private fun toLocalDate(epochDay: Long): LocalDate = LocalDate.fromEpochDays(epochDay.toInt())

    private fun weekdayLabel(epochDay: Long): String {
        val date = toLocalDate(epochDay)
        val day =
            date.dayOfWeek.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        return "$day ${date.dayOfMonth} ${date.month.name.lowercase().take(3)}"
    }

    private fun dayProvenance(entries: Int): Provenance =
        Provenance.Derived(
            formulaVersion = ConstantsRegistry.DIARY_PORTION_FORMULA_VERSION,
            inputs = listOf("entries=$entries"),
        )

    private fun slotLabel(slot: MealSlot): String =
        when (slot) {
            MealSlot.BREAKFAST -> "breakfast"
            MealSlot.LUNCH -> "lunch"
            MealSlot.DINNER -> "dinner"
            MealSlot.SNACK -> "snack"
            MealSlot.DRINK -> "drinks"
        }

    public companion object {
        /** F10 §4 / F06 §4 trend gate: ≥3 weigh-ins before the chip exists. */
        public const val TREND_GATE_MIN_POINTS: Int = 3

        /** The trailing window (days) the gate counts weigh-ins in. */
        public const val TREND_GATE_WINDOW_DAYS: Long = 7

        /** The hero delta window (the weekly rate). */
        public const val DELTA_WINDOW_DAYS: Long = 7

        /**
         * The streak's lookback window (days): the projection range fetched
         * for the run — 90 days of history bound the count; a longer streak
         * renders truncated rather than paying an unbounded read.
         */
        public const val STREAK_WINDOW_DAYS: Long = 90

        /** The action-failure notice — the house user-worded pattern (F03). */
        public const val ACTION_FAILED_NOTICE: String = "that didn't save — nothing changed"

        /** Macro pill data-viz series slots — the mock's P/C/F mapping. */
        public const val MACRO_SERIES_P: Int = 3
        public const val MACRO_SERIES_C: Int = 0
        public const val MACRO_SERIES_F: Int = 1

        /** Metric default, imperial a profile setting (R-D10). */
        public fun massUnitFor(unit: UnitSystem): MassUnit =
            when (unit) {
                UnitSystem.METRIC -> MassUnit.KILOGRAM
                UnitSystem.IMPERIAL -> MassUnit.POUND
            }

        public fun formatKcal(value: Double): String = "%,d kcal".format(value.toInt())
    }
}
