package app.wlo.feature.f06.weight.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.WeighInRepository
import app.wlo.core.designsystem.ChartPoint
import app.wlo.core.engines.BmiEngine
import app.wlo.core.engines.GirthRatiosEngine
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

    public data class MethodChange(
        public val method: TrendMethod,
    ) : WeighInEvent

    public data class AlphaChange(
        public val alpha: Double,
    ) : WeighInEvent
}

/** The derived ratios block (F06 §3, WLO-0043): computed, never entered. */
public data class RatiosUi(
    public val waistToHeight: DerivedValue<Double>?,
    public val waistToHip: DerivedValue<Double>?,
    public val bmi: DerivedValue<Double>?,
)

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

/** One compressed-history bucket, presentation-ready (WLO-0055). */
public data class HistoryBucketUi(
    public val tier: HistoryTier,
    public val label: String,
    public val countLabel: String?,
    public val deltaLabel: String?,
    public val weightLabel: String?,
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

/** The open weigh-in sheet. */
public data class SheetUi(
    public val weightText: String,
    public val dayText: String = "",
    public val timeText: String = "",
)

/** The weight surface's data state (sheet/verdict/notice are separate flows). */
public data class WeighInUiState(
    public val history: List<HistoryBucketUi>,
    public val window: ChartWindowUi,
    public val section: BodySectionUi,
    public val bodyFatPoints: List<ChartPoint>,
    public val waistPoints: List<ChartPoint>,
    public val ratios: RatiosUi?,
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
                history = emptyList(),
                window = ChartWindowUi.D90,
                section = BodySectionUi.WEIGHT,
                bodyFatPoints = emptyList(),
                waistPoints = emptyList(),
                ratios = null,
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
    private val sheet = MutableStateFlow<SheetUi?>(initialSheetOpen.takeIf { it }?.let { freshSheet() })
    private val verdict = MutableStateFlow<VerdictUi?>(null)
    private val notice = MutableStateFlow<String?>(null)
    private val data = MutableStateFlow(WeighInUiState.LOADING)

    /** Renderable data state. */
    public val uiState: StateFlow<WeighInUiState> = data

    /** The open weigh-in sheet (null = closed). */
    public val sheetState: StateFlow<SheetUi?> = sheet

    /** The outlier guard's live verdict, cleared by keep/correct. */
    public val verdictState: StateFlow<VerdictUi?> = verdict

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
                    deleteFlagged(flagged.eventId)
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

    /**
     * The sheet's date/time defaults, WITHOUT touching [uiState] — safe in
     * field initializers (the deep-link cold start composes the sheet open
     * before the first reload lands).
     */
    private fun freshSheet(weightText: String = ""): SheetUi {
        val now = clock.now().toLocalDateTime(zone)
        return SheetUi(
            weightText = weightText,
            dayText = now.date.toString(),
            timeText = "%02d:%02d".format(now.hour, now.minute),
        )
    }

    private fun openSheet(prefillKg: Double?): SheetUi =
        freshSheet(weightText = prefillKg?.let(::formatWeightInput) ?: lastWeightInput().orEmpty())

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
     * R-B8 amendment (WLO-0035): the flagged entry's delete is an explicit
     * act confirmed from the banner. The correction path is the sheet that
     * reopens prefilled from the day that remains — never the deleted value.
     * The logbook's swipe-delete undo lives on the logbook screen (WLO-0055).
     */
    private fun deleteFlagged(eventId: String) {
        val id = profileId ?: return
        viewModelScope.launch {
            when (weighIns.deleteWeighIn(eventId, clock.now())) {
                is WloResult.Ok -> {
                    sheet.value = SheetUi(lastWeightInput().orEmpty())
                    reload()
                }

                is WloResult.Err -> notice.value = "that didn't delete — nothing changed"
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

        // The compressed history (WLO-0055): one row per bucket, coarser with
        // distance — the weight surface's summary of the same store. The
        // verbatim feed is the logbook screen's; the bounded window keeps
        // this read cheap no matter how many years pile up.
        val floor = WeightHistory.floorDay(today, WeightHistory.QUARTERLY_QUARTERS)
        val history =
            measurements
                .rangeOfKind(id, MeasurementKind.WEIGHT, floor, today)
                .getOrNull()
                .orEmpty()
                .map { WeightSample(it.dayEpochDay, it.valueReal) }
                .let { WeightHistory.build(it, today) }
                .map { bucket ->
                    HistoryBucketUi(
                        tier = bucket.tier,
                        label = HistoryLabels.bucketLabel(bucket, today),
                        countLabel = "${bucket.count} weigh-in" + if (bucket.count == 1) "" else "s",
                        deltaLabel = bucket.deltaKg?.let { delta -> signedKg(delta) },
                        weightLabel = bucket.endValueKg?.let { kg -> "${formatWeightInput(kg)} kg" },
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

        // The derived ratios (F06 §3, WLO-0043): computed from the latest tape
        // + the latest daily scalar — never entered, always provenance-badged.
        val customMetrics =
            measurements
                .attrsInRange(id, from, today)
                .getOrNull()
                .orEmpty()
                .filter { it.attr == "metric" }
                .associate { it.eventId to it.valueText }
        val latestHip =
            windowEvents
                .filter { it.kind == MeasurementKind.CUSTOM && customMetrics[it.id] == "hip" }
                .maxByOrNull { it.capturedAt }
                ?.valueReal

        val samples = weighIns.dailyScalars(id, from, today).getOrNull().orEmpty()
        val ratios =
            run {
                val waistCm = waistPoints.lastOrNull()?.value
                val heightCm = profiles.active().getOrNull()?.heightCm
                val weightKg = samples.lastOrNull()?.weightKg
                RatiosUi(
                    waistToHeight =
                        if (waistCm != null && heightCm != null && heightCm > 0.0) {
                            GirthRatiosEngine.waistToHeight(waistCm, heightCm)
                        } else {
                            null
                        },
                    waistToHip =
                        if (waistCm != null && latestHip != null && latestHip > 0.0) {
                            GirthRatiosEngine.waistToHip(waistCm, latestHip)
                        } else {
                            null
                        },
                    bmi =
                        if (weightKg != null && heightCm != null && heightCm > 0.0) {
                            BmiEngine.derived(weightKg, heightCm)
                        } else {
                            null
                        },
                )
            }
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
                history = history,
                window = window.value,
                section = section.value,
                bodyFatPoints = bodyFatPoints,
                waistPoints = waistPoints,
                ratios = ratios,
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

    private fun signedKg(kg: Double): String {
        val sign = if (kg < 0) "−" else "+"
        return "$sign${formatWeightInput(abs(kg))} kg"
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
