package app.wlo.feature.f06.weight.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DeletedWeighIn
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.RoomWeighInRepository
import app.wlo.core.data.WeighInRepository
import app.wlo.core.designsystem.ChartPoint
import app.wlo.core.engines.OutlierVerdict
import app.wlo.core.engines.SmoothingEngine
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.MeasurementSource
import app.wlo.core.model.Provenance
import app.wlo.core.model.TrendMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

/** Weigh-in intents (MVI-lite). */
public sealed interface WeighInEvent {
    /** Opens the sheet; [prefillKg] seeds the field (the ±0.1 stepper works from there). */
    public data class OpenSheet(
        public val prefillKg: Double? = null,
    ) : WeighInEvent

    public data object DismissSheet : WeighInEvent

    public data class WeightChange(
        public val text: String,
    ) : WeighInEvent

    public data object StepperUp : WeighInEvent

    public data object StepperDown : WeighInEvent

    public data object Save : WeighInEvent

    /** The outlier guard's "keep" — the flagged event stays (it always did). */
    public data object KeepFlagged : WeighInEvent

    /**
     * The outlier guard's "delete" (R-B8 amendment, WLO-0035): the flagged
     * entry goes, then the sheet reopens prefilled with the last GOOD
     * reading — never the bad value.
     */
    public data object DeleteFlagged : WeighInEvent

    /** Logbook delete: one weigh-in goes; the UI keeps an in-memory undo. */
    public data class DeleteWeighIn(
        public val eventId: String,
    ) : WeighInEvent

    /** Puts the last deleted weigh-in back (re-appends the snapshot). */
    public data object UndoDelete : WeighInEvent

    /** Lets the undo notice go without undoing. */
    public data object DismissDelete : WeighInEvent

    public data class MethodChange(
        public val method: TrendMethod,
    ) : WeighInEvent

    public data class AlphaChange(
        public val alpha: Double,
    ) : WeighInEvent
}

/** One verbatim weigh-in of the day (R-B8: re-weighs are normal data). */
public data class WeighInRowUi(
    public val id: String,
    public val timeLabel: String,
    public val weightLabel: String,
    public val isLowest: Boolean,
    public val flagged: Boolean,
)

/** The trend chart + its provenance (D6: the current value is a DerivedValue). */
public data class TrendUi(
    public val samples: List<ChartPoint>,
    public val trend: List<ChartPoint>,
    public val reference: List<ChartPoint>,
    /**
     * The headline trend value: the CANONICAL shared read (the Hub shows the
     * same number — WLO-0030 defect 9) while the smoother selection sits at
     * its defaults; once the tuner moves, this becomes the tuner's preview
     * output and [preview] says so.
     */
    public val current: DerivedValue<Double>?,
    public val delta7: DerivedValue<Double>?,
    /** F06 §4 gate: the trend line only renders from ≥3 trailing-7-day points. */
    public val trendLineVisible: Boolean,
    /** True while the chart reflects a non-default tuner selection (a preview). */
    public val preview: Boolean,
    public val description: String,
)

/** The outlier confirm (one line, one tap either way — F06 §4). */
public data class VerdictUi(
    public val eventId: String,
    public val weightLabel: String,
    public val residualLabel: String,
)

/** The just-deleted weigh-in, offered back until dismissed. */
public data class DeletedUi(
    public val label: String,
)

/** The open weigh-in sheet. */
public data class SheetUi(
    public val weightText: String,
)

/** The weight surface's data state (sheet/verdict/notice are separate flows). */
public data class WeighInUiState(
    public val rows: List<WeighInRowUi>,
    public val trend: TrendUi?,
    public val method: TrendMethod,
    public val alpha: Double,
    public val lastWeighInLabel: String?,
) {
    public companion object {
        /** Lowest-of-day copy (Happy Scale's rule, spoken kindly and once). */
        public const val LOWEST_COPY: String =
            "the lowest reading of the day stands as the day's weight — re-weighs stay in the log untouched"

        public val LOADING: WeighInUiState =
            WeighInUiState(
                rows = emptyList(),
                trend = null,
                method = TrendMethod.EWMA,
                alpha = ConstantsRegistry.EWMA_ALPHA_DEFAULT,
                lastWeighInLabel = null,
            )
    }
}

/**
 * The weigh-in surface's state holder (F06 §3–4). Events append VERBATIM;
 * the daily scalar is the derived lowest-of-day view; the trend is the user's
 * smoother selection ([TrendMethod] + α, R-A2 default 0.15) computed over the
 * window. The outlier guard only informs the UI — it never drops data.
 */
public class WeighInViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val weighIns: WeighInRepository,
    private val measurements: MeasurementRepository,
    initialSheetOpen: Boolean,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private var profileId: String? = null

    private val method = MutableStateFlow(TrendMethod.EWMA)
    private val alpha = MutableStateFlow(ConstantsRegistry.EWMA_ALPHA_DEFAULT)
    private val sheet = MutableStateFlow<SheetUi?>(initialSheetOpen.takeIf { it }?.let { SheetUi("") })
    private val verdict = MutableStateFlow<VerdictUi?>(null)
    private val deleted = MutableStateFlow<DeletedUi?>(null)
    private var undoSnapshot: DeletedWeighIn? = null
    private val notice = MutableStateFlow<String?>(null)
    private val data = MutableStateFlow(WeighInUiState.LOADING)

    /** Renderable data state. */
    public val uiState: StateFlow<WeighInUiState> = data

    /** The open weigh-in sheet (null = closed). */
    public val sheetState: StateFlow<SheetUi?> = sheet

    /** The outlier guard's live verdict, cleared by keep/correct. */
    public val verdictState: StateFlow<VerdictUi?> = verdict

    /** The last deleted weigh-in, offered back until dismissed or undone. */
    public val deletedState: StateFlow<DeletedUi?> = deleted

    /** Session notices. */
    public val noticeState: StateFlow<String?> = notice

    init {
        viewModelScope.launch { reload() }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: WeighInEvent) {
        when (event) {
            is WeighInEvent.OpenSheet ->
                sheet.value = SheetUi(event.prefillKg?.let(::formatWeightInput) ?: lastWeightInput().orEmpty())
            WeighInEvent.DismissSheet -> sheet.value = null
            is WeighInEvent.WeightChange -> sheet.value = sheet.value?.copy(weightText = event.text)
            WeighInEvent.StepperUp -> step(STEP_KG)
            WeighInEvent.StepperDown -> step(-STEP_KG)
            WeighInEvent.Save -> save()
            WeighInEvent.KeepFlagged -> verdict.value = null
            WeighInEvent.DeleteFlagged ->
                verdict.value?.let { flagged ->
                    verdict.value = null
                    deleteWeighIn(flagged.eventId, reopenSheet = true)
                }

            is WeighInEvent.DeleteWeighIn -> deleteWeighIn(event.eventId, reopenSheet = false)
            WeighInEvent.UndoDelete -> undoDelete()
            WeighInEvent.DismissDelete -> {
                undoSnapshot = null
                deleted.value = null
            }

            is WeighInEvent.MethodChange ->
                viewModelScope.launch {
                    method.value = event.method
                    reload()
                }

            is WeighInEvent.AlphaChange ->
                viewModelScope.launch {
                    alpha.value = event.alpha
                    reload()
                }
        }
    }

    private fun step(delta: Double) {
        val current =
            sheet.value?.weightText?.toDoubleOrNull()
                ?: lastWeightInput()?.toDoubleOrNull()
                ?: return
        sheet.value = sheet.value?.copy(weightText = formatWeightInput(current + delta))
    }

    private fun save() {
        val id = profileId ?: return
        val kg = sheet.value?.weightText?.toDoubleOrNull() ?: return
        viewModelScope.launch {
            val outcome =
                weighIns.appendWeighIn(
                    profileId = id,
                    dayEpochDay = DayBoundary.epochDay(clock.now(), zone),
                    weightKg = kg,
                    capturedAt = clock.now(),
                    source = MeasurementSource.MANUAL,
                )
            when (outcome) {
                is WloResult.Ok -> {
                    sheet.value = null
                    (outcome.value.verdict as? OutlierVerdict.Flagged)?.let { flagged ->
                        verdict.value =
                            VerdictUi(
                                eventId = outcome.value.event.id,
                                weightLabel = formatWeightInput(outcome.value.event.valueReal),
                                residualLabel = formatResidual(flagged.residualKg),
                            )
                    }
                    reload()
                }

                is WloResult.Err -> notice.value = "that didn't save — nothing changed"
            }
        }
    }

    /**
     * R-B8 amendment (WLO-0035): the user's delete is an explicit act. The
     * snapshot rides in memory for a one-tap undo; the day's trend scalar is
     * the store's to recompute (the door handles it).
     */
    private fun deleteWeighIn(
        eventId: String,
        reopenSheet: Boolean,
    ) {
        val id = profileId ?: return
        viewModelScope.launch {
            when (val outcome = weighIns.deleteWeighIn(eventId, clock.now())) {
                is WloResult.Ok -> {
                    val event = outcome.value.event
                    undoSnapshot = outcome.value
                    deleted.value =
                        DeletedUi(label = "${formatWeightInput(event.valueReal)} kg · ${timeLabel(event.capturedAt)}")
                    reload()
                    if (reopenSheet) {
                        // Prefill from the day that remains — never the deleted value.
                        sheet.value = SheetUi(lastWeightInput().orEmpty())
                    }
                    Unit
                }

                is WloResult.Err -> notice.value = "that didn't delete — nothing changed"
            }
        }
    }

    private fun undoDelete() {
        val id = profileId ?: return
        val snapshot = undoSnapshot ?: return
        viewModelScope.launch {
            val event = snapshot.event
            val reappended =
                weighIns.appendWeighIn(
                    profileId = id,
                    dayEpochDay = event.dayEpochDay,
                    weightKg = event.valueReal,
                    capturedAt = event.capturedAt,
                    source = event.source,
                    note = event.note,
                )
            when (reappended) {
                is WloResult.Ok -> {
                    if (snapshot.attrs.isNotEmpty()) {
                        measurements.attachAttrs(reappended.value.event.id, snapshot.attrs)
                    }
                    undoSnapshot = null
                    deleted.value = null
                    reload()
                }

                is WloResult.Err -> notice.value = "that didn't come back — it stays deleted"
            }
        }
    }

    private suspend fun reload() {
        val id = profileId ?: profiles.active().getOrNull()?.id ?: return
        profileId = id
        val unit = MassUnit.KILOGRAM
        val today = DayBoundary.epochDay(clock.now(), zone)
        val from = today - CHART_WINDOW_DAYS + 1

        val dayEvents = weighIns.dayWeighIns(id, today).getOrNull().orEmpty()
        val lowest = weighIns.lowestOfDay(id, today).getOrNull()
        val flaggedIds = mutableSetOf<String>()
        for (event in dayEvents) {
            val attrs = measurements.attrsOf(event.id).getOrNull().orEmpty()
            if (attrs.any { it.attr == RoomWeighInRepository.OUTLIER_ATTR }) flaggedIds += event.id
        }

        val samples = weighIns.dailyScalars(id, from, today).getOrNull().orEmpty()
        val series = weighIns.trend(id, from, today, method.value, alpha.value).getOrNull()
        val points = series?.points.orEmpty()
        // THE single trend source (WLO-0030 defect 9): the canonical
        // read — the exact number the Hub shows. The tuner's own output
        // is a preview and is labeled as one.
        val canonical = weighIns.currentTrend(id, today).getOrNull()
        val atDefaults =
            method.value == TrendMethod.EWMA &&
                abs(alpha.value - ConstantsRegistry.EWMA_ALPHA_DEFAULT) < 1e-9

        val byDay = points.associate { it.epochDay to it.trendKg.value }
        val reference =
            points.mapNotNull { point ->
                byDay[point.epochDay - REFERENCE_SHIFT_DAYS]?.let { behind -> ChartPoint(point.epochDay, behind) }
            }
        val lastPoint = points.lastOrNull()
        val weekAgo = lastPoint?.epochDay?.let { byDay[it - DELTA_WINDOW_DAYS] }
        val tunerDelta =
            if (lastPoint != null && weekAgo != null) {
                DerivedValue(
                    lastPoint.trendKg.value - weekAgo,
                    Provenance.Derived(
                        formulaVersion = seriesVersion(method.value),
                        inputs = listOf("windowDays=$DELTA_WINDOW_DAYS"),
                    ),
                )
            } else {
                null
            }
        val delta = if (atDefaults) canonical?.delta7 else tunerDelta

        val trailing7 = samples.count { it.epochDay > today - TREND_GATE_WINDOW_DAYS }
        val trendVisible = trailing7 >= TREND_GATE_POINTS

        data.value =
            WeighInUiState(
                rows =
                    dayEvents.map { event ->
                        WeighInRowUi(
                            id = event.id,
                            timeLabel = timeLabel(event.capturedAt),
                            weightLabel = unit.format(event.valueReal),
                            isLowest = lowest?.id == event.id && dayEvents.size > 1,
                            flagged = event.id in flaggedIds,
                        )
                    },
                trend =
                    TrendUi(
                        samples = samples.map { ChartPoint(it.epochDay, it.weightKg) },
                        trend =
                            if (trendVisible) {
                                points.map { ChartPoint(it.epochDay, it.trendKg.value) }
                            } else {
                                emptyList()
                            },
                        reference = reference,
                        current = if (atDefaults) canonical?.current else lastPoint?.trendKg,
                        delta7 = delta,
                        trendLineVisible = trendVisible,
                        preview = !atDefaults,
                        description =
                            if (trendVisible) {
                                "Weight chart: scale dots with the trend line over them."
                            } else {
                                "Weight chart: scale dots only — keep weighing, the trend forms in a few days."
                            },
                    ),
                method = method.value,
                alpha = alpha.value,
                lastWeighInLabel =
                    dayEvents.lastOrNull()?.let { event ->
                        "${unit.format(event.valueReal)} · ${timeLabel(event.capturedAt)}"
                    },
            )
    }

    // --- small helpers ---

    private fun lastWeightInput(): String? =
        uiState.value.lastWeighInLabel
            ?.substringBefore(" ·")
            ?.let { it.substringBefore(" ") }

    private fun timeLabel(instant: Instant): String {
        val local = instant.toLocalDateTime(zone)
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    private fun formatWeightInput(kg: Double): String {
        val tenths = (kg * 10).toLong()
        val whole = tenths / 10
        val tenth = tenths % 10
        return "$whole.$tenth"
    }

    private fun formatResidual(kg: Double): String {
        val sign = if (kg < 0) "−" else "+"
        return "$sign ${formatWeightInput(abs(kg))} kg"
    }

    private fun seriesVersion(method: TrendMethod): String =
        when (method) {
            TrendMethod.EWMA -> SmoothingEngine.EWMA_VERSION
            TrendMethod.ZERO_PHASE_EWMA -> SmoothingEngine.ZERO_PHASE_VERSION
            TrendMethod.MOVING_AVERAGE_7D -> SmoothingEngine.MA7_VERSION
        }

    public companion object {
        /** The chart window (days) — a season at a glance; zooming is F11's. */
        public const val CHART_WINDOW_DAYS: Long = 90

        /** Trend-line gate (F06 §4: ≥3 points in the trailing 7 days). */
        public const val TREND_GATE_POINTS: Int = 3

        /** Trailing window (days) the trend gate counts points in. */
        public const val TREND_GATE_WINDOW_DAYS: Long = 7

        /** The progress-ribbon reference shift (days-ago line, default 30). */
        public const val REFERENCE_SHIFT_DAYS: Long = 30

        /** The headline delta window (F06 §5's weekly rate reads weekly). */
        public const val DELTA_WINDOW_DAYS: Long = 7

        /** The ±0.1 prefill stepper (F10 §4 morning flow). */
        public const val STEP_KG: Double = 0.1
    }
}
