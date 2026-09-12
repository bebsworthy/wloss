package app.wlo.app.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.MassUnit
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.DayView
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.TargetsRepository
import app.wlo.core.documents.DietTemplateApplier
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.engines.ColdStartInput
import app.wlo.core.engines.ForecastBands
import app.wlo.core.engines.ForecastEngine
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
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
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * Hub state (F10 surface, M2): sourced from the data spine — trend + budget
 * from the day projection (Appendix A.3), the R-A5 cold-start forecast from
 * the F07 engine. Every number travels as [DerivedValue]; rendering goes only
 * through provenance-chip components (D6).
 */
public sealed interface HubUiState {
    /** First emissions in flight. */
    public data object Loading : HubUiState

    /** No active profile — the shell gate shows the wizard instead of this. */
    public data object Fresh : HubUiState

    public data class Ready(
        public val todayLabel: String,
        public val trend: DerivedValue<String>,
        public val budget: DerivedValue<String>?,
        public val forecast: HubForecast?,
        public val explainer: ExplainerUi? = null,
    ) : HubUiState
}

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
 * M2 hub state holder. The forecast is the R-A5 cold-start mode — it shows
 * from day zero, ESTIMATED-chipped, before any weigh-in exists; F07's
 * measured mode replaces it in a later milestone. Explainer state is
 * session-local UI state so the sheet survives recomposition.
 */
public class HubViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val dayProjection: DayProjectionRepository,
    private val targets: TargetsRepository,
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
            .map(::render)

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

    private fun render(inputs: HubInputs?): HubUiState {
        if (inputs == null) return HubUiState.Fresh
        val (profile, day, current) = inputs
        val unit = massUnitFor(profile.unitPreference)

        val trend =
            day?.trendWeightKg?.let { DerivedValue(unit.format(it.value), it.provenance) }
                ?: DerivedValue(
                    unit.format(profile.startWeightKg),
                    Provenance.Measured(at = profile.createdAt, instrument = "start-weight entry"),
                )

        val budget = day?.budgetKcal?.let { DerivedValue(formatKcal(it.value), it.provenance) }
        val forecast = current?.let { coldStartForecast(profile, it, day, unit) }

        return HubUiState.Ready(
            todayLabel = weekdayLabel(today),
            trend = trend,
            budget = budget,
            forecast = forecast,
        )
    }

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

    private fun weekdayLabel(epochDay: Long): String {
        val iso = LocalDate.fromEpochDays(epochDay.toInt()).dayOfWeek.isoDayNumber
        return WEEKDAYS[iso - 1]
    }

    private companion object {
        val WEEKDAYS =
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        /** Metric default, imperial a profile setting (R-D10). */
        fun massUnitFor(unit: UnitSystem): MassUnit =
            when (unit) {
                UnitSystem.METRIC -> MassUnit.KILOGRAM
                UnitSystem.IMPERIAL -> MassUnit.POUND
            }

        fun formatKcal(value: Double): String = "%,d kcal".format(value.toInt())
    }
}
