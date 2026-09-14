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
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.model.Provenance
import app.wlo.core.model.TrendMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
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

    /** The sheet's date field, ISO YYYY-MM-DD — the back-datable pad (F06 §4). */
    public data class SheetDayChange(
        public val text: String,
    ) : WeighInEvent

    /** The sheet's time field, HH:MM; blank means noon (the R-B5 normalization). */
    public data class SheetTimeChange(
        public val text: String,
    ) : WeighInEvent

    /** The chart window (F06 §5: 30d/90d/1y/all). */
    public data class WindowChange(
        public val window: ChartWindowUi,
    ) : WeighInEvent

    /** The weight surface's segment (WLO-0035 R2: same screen, different series). */
    public data class SectionChange(
        public val section: BodySectionUi,
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

/** The weight surface's segments (R2: weight and body fat live together). */
public enum class BodySectionUi {
    WEIGHT,
    BODY_FAT,
}

/** The chart window (F06 §5); `days = null` means "all". */
public enum class ChartWindowUi(
    public val label: String,
    public val days: Long?,
) {
    D30("30 d", 30),
    D90("90 d", 90),
    Y1("1 y", 365),
    ALL("all", null),
}

/** One logbook row: a raw weigh-in with its provenance marks (F06 §5). */
public data class LogbookRowUi(
    public val id: String,
    public val timeLabel: String,
    public val weightLabel: String,
    public val isLowest: Boolean,
    public val flagged: Boolean,
)

/** One day of the logbook, newest first — the raw ground truth beneath the trend. */
public data class LogbookDayUi(
    public val dayEpochDay: Long,
    public val dayLabel: String,
    public val isToday: Boolean,
    public val rows: List<LogbookRowUi>,
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
    public val dayText: String = "",
    public val timeText: String = "",
)

/** The weight surface's data state (sheet/verdict/notice are separate flows). */
public data class WeighInUiState(
    public val logbook: List<LogbookDayUi>,
    public val window: ChartWindowUi,
    public val section: BodySectionUi,
    public val bodyFatPoints: List<ChartPoint>,
    public val waistPoints: List<ChartPoint>,
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
                logbook = emptyList(),
                window = ChartWindowUi.D90,
                section = BodySectionUi.WEIGHT,
                bodyFatPoints = emptyList(),
                waistPoints = emptyList(),
                trend = null,
                method = TrendMethod.EWMA,
                alpha = ConstantsRegistry.EWMA_ALPHA_DEFAULT,
                lastWeighInLabel = null,
            )
    }
}

/**
 * The back-datable pad's parser (F06 §4): ISO date, optional HH:MM time —
 * blank means noon, the R-B5 scalar normalization, an honest choice for a
 * reading whose time is forgotten. The past only: the future is refused.
 */
internal object SheetWhenParser {
    public fun parse(
        dayText: String,
        timeText: String,
        zone: TimeZone,
        now: Instant,
    ): Pair<Long, Instant>? {
        val date = runCatching { LocalDate.parse(dayText.trim()) }.getOrNull() ?: return null
        val trimmed = timeText.trim()
        val time =
            if (trimmed.isEmpty()) {
                LocalTime.fromSecondOfDay(NOON_SECONDS)
            } else {
                runCatching { LocalTime.parse(trimmed) }.getOrNull() ?: return null
            }
        val at = LocalDateTime(date, time).toInstant(zone)
        if (at > now) return null
        return date.toEpochDays() to at
    }

    private const val NOON_SECONDS: Int = 12 * 60 * 60
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
    initialSection: BodySectionUi = BodySectionUi.WEIGHT,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private var profileId: String? = null

    private val method = MutableStateFlow(TrendMethod.EWMA)
    private val alpha = MutableStateFlow(ConstantsRegistry.EWMA_ALPHA_DEFAULT)
    private val window = MutableStateFlow(ChartWindowUi.D90)
    private val section = MutableStateFlow(initialSection)
    private val sheet = MutableStateFlow<SheetUi?>(initialSheetOpen.takeIf { it }?.let { openSheet(prefillKg = null) })
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
            is WeighInEvent.OpenSheet -> sheet.value = openSheet(event.prefillKg)
            WeighInEvent.DismissSheet -> sheet.value = null
            is WeighInEvent.WeightChange -> sheet.value = sheet.value?.copy(weightText = event.text)
            is WeighInEvent.SheetDayChange -> sheet.value = sheet.value?.copy(dayText = event.text)
            is WeighInEvent.SheetTimeChange -> sheet.value = sheet.value?.copy(timeText = event.text)
            is WeighInEvent.WindowChange ->
                viewModelScope.launch {
                    window.value = event.window
                    reload()
                }

            is WeighInEvent.SectionChange -> section.value = event.section
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

    private fun openSheet(prefillKg: Double?): SheetUi {
        val now = clock.now().toLocalDateTime(zone)
        return SheetUi(
            weightText = prefillKg?.let(::formatWeightInput) ?: lastWeightInput().orEmpty(),
            dayText = now.date.toString(),
            timeText = "%02d:%02d".format(now.hour, now.minute),
        )
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
        val current = sheet.value ?: return
        val kg = current.weightText.toDoubleOrNull() ?: return
        val whenLogged =
            SheetWhenParser.parse(current.dayText, current.timeText, zone, clock.now())
        if (whenLogged == null) {
            notice.value = "check the date and time — YYYY-MM-DD and HH:MM, today or earlier"
            return
        }
        val (day, at) = whenLogged
        viewModelScope.launch {
            val outcome =
                weighIns.appendWeighIn(
                    profileId = id,
                    dayEpochDay = day,
                    weightKg = kg,
                    capturedAt = at,
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
        val from = window.value.days?.let { today - it + 1 } ?: 0L

        val dayEvents = weighIns.dayWeighIns(id, today).getOrNull().orEmpty()
        val flaggedIds =
            measurements
                .attrsInRange(id, 0, today)
                .getOrNull()
                .orEmpty()
                .filter { it.attr == RoomWeighInRepository.OUTLIER_ATTR }
                .map { it.eventId }
                .toSet()

        // The logbook (F06 §5): every raw weigh-in, day-grouped, newest first —
        // the geek's ground truth beneath every smoothed view.
        val logbook =
            measurements
                .range(id, 0, today)
                .getOrNull()
                .orEmpty()
                .filter { it.kind == MeasurementKind.WEIGHT }
                .groupBy { it.dayEpochDay }
                .toSortedMap(compareByDescending { it })
                .map { (day, events) ->
                    val lowestId = events.minByOrNull { it.valueReal }?.id
                    LogbookDayUi(
                        dayEpochDay = day,
                        dayLabel = dayLabel(day, today),
                        isToday = day == today,
                        rows =
                            events.map { event ->
                                LogbookRowUi(
                                    id = event.id,
                                    timeLabel = timeLabel(event.capturedAt),
                                    weightLabel = unit.format(event.valueReal),
                                    isLowest = event.id == lowestId && events.size > 1,
                                    flagged = event.id in flaggedIds,
                                )
                            },
                    )
                }

        // The companion series (WLO-0035 R2): body-fat estimates in % and the
        // waist tape in cm — same store, same window, no cap anywhere.
        val windowEvents = measurements.range(id, from, today).getOrNull().orEmpty()
        val waistEventIds =
            measurements
                .attrsInRange(id, from, today)
                .getOrNull()
                .orEmpty()
                .filter { it.attr == "metric" && it.valueText == "waist" }
                .map { it.eventId }
                .toSet()
        val bodyFatPoints =
            windowEvents
                .filter { it.kind == MeasurementKind.BODY_FAT }
                .map { ChartPoint(it.dayEpochDay, it.valueReal) }
        val waistPoints =
            windowEvents
                .filter { it.kind == MeasurementKind.CUSTOM && it.id in waistEventIds }
                .map { ChartPoint(it.dayEpochDay, it.valueReal) }

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
                logbook = logbook,
                window = window.value,
                section = section.value,
                bodyFatPoints = bodyFatPoints,
                waistPoints = waistPoints,
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

    private fun dayLabel(
        day: Long,
        today: Long,
    ): String =
        when (day) {
            today -> "Today"
            today - 1L -> "Yesterday"
            else -> {
                val date = LocalDate.fromEpochDays(day)
                "${WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]} ${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
            }
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

        private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        private val MONTHS =
            listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

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
