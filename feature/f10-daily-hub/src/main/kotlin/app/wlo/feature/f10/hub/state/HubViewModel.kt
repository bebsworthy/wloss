package app.wlo.feature.f10.hub.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.MassUnit
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.DayView
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.TargetsRepository
import app.wlo.core.data.WeighInRepository
import app.wlo.core.documents.DietTemplateApplier
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.engines.ColdStartInput
import app.wlo.core.engines.DayModel
import app.wlo.core.engines.DayModelEngine
import app.wlo.core.engines.DayModelInput
import app.wlo.core.engines.DayPhase
import app.wlo.core.engines.ForecastBands
import app.wlo.core.engines.ForecastEngine
import app.wlo.core.engines.TrendSeries
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.MealSlot
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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
        /** The hero's trend-first number + the weekly delta (null until gated in). */
        public val heroTrend: DerivedValue<String>,
        public val heroDelta: DerivedValue<Double>?,
        public val trendChipAvailable: Boolean,
        public val budget: DerivedValue<String>?,
        public val burn: DerivedValue<String>?,
        public val diarySlice: DiarySliceUi?,
        public val heatmap: HeatmapUi?,
        public val forecast: HubForecast?,
        public val explainer: ExplainerUi? = null,
    ) : HubUiState
}

/** The diary's today slice (F10 renders it; F02 owns the diary, R-B1). */
public data class DiarySliceUi(
    public val kcal: DerivedValue<Double>,
    public val entryCount: Int,
    public val slotSummary: String,
)

/** The month heatmap payload — diary density (R-D4: THE shared heatmap). */
public data class HeatmapUi(
    public val values: Map<Long, Double>,
    public val monthStart: LocalDate,
    public val description: String,
    /** Today's epoch day — days after it render empty, never as gaps. */
    public val upTo: Long,
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
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private val today: Long = DayBoundary.epochDay(clock.now(), zone)
    private val now: Instant = clock.now()

    private val explainer: MutableStateFlow<ExplainerUi?> = MutableStateFlow(null)

    /** MVI-lite intent entry point. */
    public fun onEvent(event: HubEvent) {
        when (event) {
            is HubEvent.ShowExplainer -> explainer.value = event.explainer
            HubEvent.DismissExplainer -> explainer.value = null
        }
    }

    /** Renderable hub state. */
    public val uiState: StateFlow<HubUiState> =
        combine(inputs(), explainer, ::withExplainer).stateIn(
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

    private fun withExplainer(
        ready: HubUiState,
        explainerUi: ExplainerUi?,
    ): HubUiState = if (ready is HubUiState.Ready) ready.copy(explainer = explainerUi) else ready

    private suspend fun render(inputs: HubInputs?): HubUiState {
        if (inputs == null) return HubUiState.Fresh
        val (profile, day, current) = inputs
        val unit = massUnitFor(profile.unitPreference)

        val trendValue = day?.trendWeightKg?.value ?: profile.startWeightKg
        val trendProvenance =
            day?.trendWeightKg?.provenance
                ?: Provenance.Measured(at = profile.createdAt, instrument = "start-weight entry")
        val heroTrend = DerivedValue(unit.format(trendValue), trendProvenance)

        val budget = day?.budgetKcal?.let { DerivedValue(formatKcal(it.value), it.provenance) }
        val burn =
            day?.burnKcal?.takeIf { it.value > 0.0 }?.let { DerivedValue(formatKcal(it.value), it.provenance) }
        val forecast = current?.let { coldStartForecast(profile, it, day, unit) }

        val diarySlice = renderDiarySlice(profile.id)
        val heatmap = renderHeatmap(profile.id)
        val delta = trendDelta7(profile.id)

        // The Day Model rules (F10 §3). Completeness inputs the features own:
        // the weigh-in flag and the F10 §4 trend gate; plan/workout/check-in
        // flags arrive with F03/F05/F07 — content-rendered, absence is silent.
        val localTime = now.toLocalDateTime(zone).time
        val dayModel =
            DayModelEngine.resolve(
                DayModelInput(
                    minutesOfDay = localTime.hour * 60 + localTime.minute,
                    weighInLogged = dayWeighInCount(profile.id) > 0,
                    trendAvailable = delta != null,
                    hasOpenPlannedMeal = false,
                    workoutDueToday = false,
                    checkInDueToday = false,
                    isPlanner = false,
                ),
            )

        return HubUiState.Ready(
            todayLabel = weekdayLabel(today),
            phase = dayModel.phase,
            dayModel = dayModel,
            heroTrend = heroTrend,
            heroDelta = delta,
            trendChipAvailable = delta != null,
            budget = budget,
            burn = burn,
            diarySlice = diarySlice,
            heatmap = heatmap,
            forecast = forecast,
        )
    }

    // --- diary slice + heatmap (R-D4) ---

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
        )
    }

    /**
     * The month heatmap of logged days (diary density): F02 §5's logged-days
     * heatmap rendered on the Hub's diary card through the ONE shared
     * designsystem component (R-D4), later reused by F05/F09/F11.
     */
    private suspend fun renderHeatmap(profileId: String): HeatmapUi? {
        val todayDate = toLocalDate(today)
        val monthStart = LocalDate(year = todayDate.year, monthNumber = todayDate.monthNumber, dayOfMonth = 1)
        val from = monthStart.toEpochDays().toLong()
        val views = dayProjection.range(profileId, from, today).getOrNull().orEmpty()
        val maxKcal = views.mapNotNull { it.intakeKcal?.value }.maxOrNull() ?: return null
        if (maxKcal <= 0.0) return null
        val values =
            views
                .mapNotNull { view -> view.intakeKcal?.let { view.dayEpochDay to it.value } }
                .toMap()
                .mapValues { (_, kcal) -> (kcal / maxKcal).coerceIn(0.0, 1.0) }
        return HeatmapUi(
            values = values,
            monthStart = monthStart,
            description = "logged days this month, energy density tinted",
            upTo = today,
        )
    }

    // --- F06 flags ---

    private suspend fun dayWeighInCount(profileId: String): Int =
        weighIns
            .dayWeighIns(profileId, today)
            .getOrNull()
            .orEmpty()
            .size

    /** The weekly trend delta for the hero chip ("Trend 179.1, down 0.6"); null until F10 §4's gate passes. */
    private suspend fun trendDelta7(profileId: String): DerivedValue<Double>? {
        val scalars =
            weighIns
                .dailyScalars(profileId, today - TREND_GATE_WINDOW_DAYS + 1, today)
                .getOrNull()
                .orEmpty()
        if (scalars.size < TREND_GATE_MIN_POINTS) return null
        val series = weighIns.trend(profileId, today - DELTA_WINDOW_DAYS * 2, today).getOrNull() ?: return null
        val byDay = series.points.associate { it.epochDay to it.trendKg.value }
        val lastDay = series.points.lastOrNull()?.epochDay ?: return null
        val weekAgo = byDay[lastDay - DELTA_WINDOW_DAYS] ?: return null
        val current = byDay[lastDay] ?: return null
        return DerivedValue(
            current - weekAgo,
            Provenance.Derived(
                formulaVersion = series.formulaVersion(),
                inputs = listOf("windowDays=$DELTA_WINDOW_DAYS"),
            ),
        )
    }

    private fun TrendSeries.formulaVersion(): String =
        points.lastOrNull()?.trendKg?.provenance?.let { provenance ->
            (provenance as? Provenance.Derived)?.formulaVersion
        } ?: ConstantsRegistry.EWMA_FORMULA_VERSION

    // --- forecast (R-A5 cold start) ---

    /** R-A5 cold-start forecast (mandatory v1): visible before any weigh-in. */
    private fun coldStartForecast(
        profile: Profile,
        record: TargetsRecord,
        day: DayView?,
        unit: MassUnit,
    ): HubForecast? {
        val goal = record.document.goal
        val startTrend = day?.trendWeightKg?.value ?: profile.startWeightKg
        if (goal.targetWeightKg >= startTrend) return null
        val intake = day?.budgetKcal?.value ?: DietTemplateApplier.DEFAULT_BUDGET_KCAL
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

        /** Metric default, imperial a profile setting (R-D10). */
        public fun massUnitFor(unit: UnitSystem): MassUnit =
            when (unit) {
                UnitSystem.METRIC -> MassUnit.KILOGRAM
                UnitSystem.IMPERIAL -> MassUnit.POUND
            }

        public fun formatKcal(value: Double): String = "%,d kcal".format(value.toInt())
    }
}
