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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Provided
import kotlin.math.abs
import kotlin.math.roundToLong
import app.wlo.core.engines.WeightSample as EngineWeightSample

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

    /** The confirmation card's explicit return to the Weight surface. */
    public data object DismissConfirmation : WeighInEvent

    /** Acknowledges the one-shot save haptic for [eventId]. */
    public data class ConfirmationHapticConsumed(
        public val eventId: String,
    ) : WeighInEvent

    /** Re-reads time-sensitive state after resume, a boundary, or an explicit retry. */
    public data object Refresh : WeighInEvent

    /** Cheap foreground poll; only reads repositories when local day or timezone changed. */
    public data object BoundaryCheck : WeighInEvent

    /** The outlier guard's "keep" — the flagged event stays (it always did). */
    public data object KeepFlagged : WeighInEvent

    /**
     * The outlier guard's "delete" (R-B8 amendment, WLO-0035): the flagged
     * entry goes, then the sheet reopens prefilled with the last GOOD
     * reading — never the bad value.
     */
    public data object CorrectFlagged : WeighInEvent

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
    /**
     * The headline trend value: the CANONICAL shared read (the Hub shows the
     * same number — WLO-0030 defect 9) while the smoother selection sits at
     * its defaults; once the tuner moves, this becomes the tuner's preview
     * output and [preview] says so.
     */
    public val current: DerivedValue<Double>?,
    public val delta7: DerivedValue<Double>?,
    /** Neutral change across the canonical trailing 30-calendar-day window. */
    public val delta30: DerivedValue<Double>?,
    /** F06 §4 gate: the trend line only renders from ≥3 points in the selected window. */
    public val trendLineVisible: Boolean,
    /** True while the chart reflects a non-default tuner selection (a preview). */
    public val preview: Boolean,
    /** Explicit chart domain; sparse data must not silently stretch to fill the plot. */
    public val windowStartDay: Long,
    public val windowEndDay: Long,
    public val alwaysShowTickYear: Boolean,
    /** Honest warm-up/lapsed/empty copy rendered with the chart. */
    public val stateCopy: String?,
    /** Smallest wider window containing data, offered only for an empty selected window. */
    public val emptyActionWindow: ChartWindowUi?,
    public val description: String,
)

/** The outlier confirm (one line, one tap either way — F06 §4). */
public data class VerdictUi(
    public val eventId: String,
    public val weightLabel: String,
    public val residualLabel: String,
)

/**
 * The post-commit receipt. Raw data comes from the event returned by the
 * append, while trend data comes from the canonical read performed after
 * that append has rebuilt projections (WLO-0070). [hapticPending] is consumed
 * by the UI so ordinary recomposition cannot replay the save haptic.
 */
public data class WeighInConfirmationUi(
    public val eventId: String,
    public val rawWeightKg: Double,
    public val source: String,
    public val trend: DerivedValue<Double>?,
    public val delta7: DerivedValue<Double>?,
    public val sampleCount: Int,
    public val hapticPending: Boolean = true,
)

/** The open weigh-in sheet. */
public data class SheetUi(
    public val weightText: String,
    public val dayText: String = "",
    public val timeText: String = "",
    public val prefillContext: String = "No previous reading yet.",
    public val weightError: String? = null,
    public val whenError: String? = null,
    public val saveError: String? = null,
)

/** Explicit lifecycle for one save attempt; repeat taps are ignored while saving. */
public enum class WeighInSubmissionState {
    IDLE,
    INVALID,
    SAVING,
    SUCCESS,
    FAILURE,
}

/** Read lifecycle is separate from content so a failure can retain the last good snapshot. */
public sealed interface WeightLoadState {
    public data object Loading : WeightLoadState

    public data object Empty : WeightLoadState

    public data object Content : WeightLoadState

    public data class Error(
        public val message: String,
    ) : WeightLoadState
}

/** A validated manual weight, converted exactly once into canonical kilograms. */
internal data class ParsedWeight(
    val kilograms: Double,
)

private data class EntryPrefill(
    val kilograms: Double?,
    val context: String,
)

/** Locale-tolerant parsing and canonical-range validation for manual weigh-ins. */
internal object WeighInInputParser {
    fun parse(
        text: String,
        unit: MassUnit,
    ): Result<ParsedWeight> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Enter a weight."))
        if (trimmed.count { it == '.' || it == ',' } > 1) {
            return Result.failure(IllegalArgumentException("Enter one number, for example 78.4."))
        }
        val displayValue = trimmed.replace(',', '.').toDoubleOrNull()
        if (displayValue == null || !displayValue.isFinite()) {
            return Result.failure(IllegalArgumentException("Enter one number, for example 78.4."))
        }
        val kilograms = unit.toKilograms(displayValue)
        if (!kilograms.isFinite() || kilograms !in MIN_KG..MAX_KG) {
            val lower = unit.formatNumber(MIN_KG)
            val upper = unit.formatNumber(MAX_KG)
            return Result.failure(
                IllegalArgumentException("Enter a weight from $lower to $upper ${unit.symbol}."),
            )
        }
        return Result.success(ParsedWeight(kilograms))
    }

    private const val MIN_KG: Double = 30.0
    private const val MAX_KG: Double = 300.0
}

/** The weight surface's data state (sheet/verdict/notice are separate flows). */
public data class WeighInUiState(
    public val loadState: WeightLoadState,
    public val massUnit: MassUnit,
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
    /** Canonical kilograms used to seed the next manual entry in the active display unit. */
    public val entryPrefillKg: Double?,
    public val entryPrefillContext: String,
    public val goalProgress: GoalProgressUi,
) {
    public companion object {
        /** Lowest-of-day copy (Happy Scale's rule, spoken kindly and once). */
        public const val LOWEST_COPY: String =
            "the lowest reading of the day stands as the day's weight — re-weighs stay in the log untouched"

        public val LOADING: WeighInUiState =
            WeighInUiState(
                loadState = WeightLoadState.Loading,
                massUnit = MassUnit.KILOGRAM,
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
                entryPrefillKg = null,
                entryPrefillContext = "No previous reading yet.",
                goalProgress = GoalProgressUi(GoalProgressState.NO_GOAL, null, null, null),
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
    private val goalProgressLoader: GoalProgressLoader,
    @Provided private val massUnits: Flow<MassUnit>,
    initialSheetOpen: Boolean,
    initialSection: BodySectionUi = BodySectionUi.WEIGHT,
    private val zoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {
    private var profileId: String? = null
    private var activeUnit: MassUnit = MassUnit.KILOGRAM

    private val method = MutableStateFlow(TrendMethod.EWMA)
    private val alpha = MutableStateFlow(ConstantsRegistry.EWMA_ALPHA_DEFAULT)
    private val window = MutableStateFlow(ChartWindowUi.D90)
    private val section = MutableStateFlow(initialSection)
    private var sheetPrefillPending: Boolean = initialSheetOpen
    private val sheet = MutableStateFlow<SheetUi?>(initialSheetOpen.takeIf { it }?.let { freshSheet() })
    private val verdict = MutableStateFlow<VerdictUi?>(null)
    private val confirmation = MutableStateFlow<WeighInConfirmationUi?>(null)
    private val notice = MutableStateFlow<String?>(null)
    private val submission = MutableStateFlow(WeighInSubmissionState.IDLE)
    private val data = MutableStateFlow(WeighInUiState.LOADING)
    private var previewReload: Job? = null
    private var loadGeneration: Long = 0
    private var loadedTemporalKey: WeightTemporalKey? = null

    /** Renderable data state. */
    public val uiState: StateFlow<WeighInUiState> = data

    /** The open weigh-in sheet (null = closed). */
    public val sheetState: StateFlow<SheetUi?> = sheet

    /** The outlier guard's live verdict, cleared by keep/correct. */
    public val verdictState: StateFlow<VerdictUi?> = verdict

    /** The persisted-event + canonical-trend confirmation, or null after Done. */
    public val confirmationState: StateFlow<WeighInConfirmationUi?> = confirmation

    /** Session notices. */
    public val noticeState: StateFlow<String?> = notice

    /** Save lifecycle, exposed separately so success remains observable after the sheet closes. */
    public val submissionState: StateFlow<WeighInSubmissionState> = submission

    init {
        viewModelScope.launch {
            massUnits
                .catch { data.value = data.value.copy(loadState = WeightLoadState.Error(LOAD_FAILED_NOTICE)) }
                .collectLatest { unit ->
                    activeUnit = unit
                    refresh()
                }
        }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: WeighInEvent) {
        when (event) {
            is WeighInEvent.OpenSheet -> {
                confirmation.value = null
                sheet.value = openSheet(event.prefillKg)
            }
            WeighInEvent.DismissSheet -> {
                sheet.value = null
                sheetPrefillPending = false
                submission.value = WeighInSubmissionState.IDLE
            }
            is WeighInEvent.WeightChange -> {
                sheetPrefillPending = false
                sheet.value = sheet.value?.copy(weightText = event.text, weightError = null, saveError = null)
                submission.value = WeighInSubmissionState.IDLE
            }
            is WeighInEvent.SheetDayChange -> {
                sheet.value = sheet.value?.copy(dayText = event.text, whenError = null, saveError = null)
                submission.value = WeighInSubmissionState.IDLE
            }
            is WeighInEvent.SheetTimeChange -> {
                sheet.value = sheet.value?.copy(timeText = event.text, whenError = null, saveError = null)
                submission.value = WeighInSubmissionState.IDLE
            }
            is WeighInEvent.WindowChange ->
                requestPreviewReload { window.value = event.window }

            is WeighInEvent.SectionChange -> section.value = event.section
            WeighInEvent.StepperUp -> step(STEP_DISPLAY_UNIT)
            WeighInEvent.StepperDown -> step(-STEP_DISPLAY_UNIT)
            WeighInEvent.Save -> save()
            WeighInEvent.DismissConfirmation -> confirmation.value = null
            is WeighInEvent.ConfirmationHapticConsumed -> {
                val current = confirmation.value
                if (current?.eventId == event.eventId && current.hapticPending) {
                    confirmation.value = current.copy(hapticPending = false)
                }
            }
            WeighInEvent.Refresh -> refresh()
            WeighInEvent.BoundaryCheck -> refreshIfBoundaryChanged()
            WeighInEvent.KeepFlagged -> verdict.value = null
            WeighInEvent.CorrectFlagged ->
                verdict.value?.let { flagged ->
                    verdict.value = null
                    confirmation.value = null
                    deleteFlagged(flagged.eventId)
                }

            is WeighInEvent.MethodChange ->
                renderTunerPreview { method.value = event.method }

            is WeighInEvent.AlphaChange ->
                renderTunerPreview { alpha.value = event.alpha }
        }
    }

    /**
     * The sheet's date/time defaults, WITHOUT touching [uiState] — safe in
     * field initializers (the deep-link cold start composes the sheet open
     * before the first reload lands).
     */
    private fun freshSheet(
        weightText: String = "",
        prefillContext: String = "No previous reading yet.",
    ): SheetUi {
        val now = clock.now().toLocalDateTime(zoneProvider())
        return SheetUi(
            weightText = weightText,
            dayText = now.date.toString(),
            timeText = "%02d:%02d".format(now.hour, now.minute),
            prefillContext = prefillContext,
        )
    }

    private fun openSheet(prefillKg: Double?): SheetUi {
        submission.value = WeighInSubmissionState.IDLE
        val valueKg = prefillKg ?: uiState.value.entryPrefillKg
        val context =
            when {
                prefillKg != null -> "Prefilled from your previous reliable reading."
                valueKg != null -> uiState.value.entryPrefillContext
                else -> "No previous reading yet."
            }
        sheetPrefillPending = valueKg == null
        return freshSheet(
            weightText = valueKg?.let(activeUnit::formatNumber).orEmpty(),
            prefillContext = context,
        )
    }

    private fun step(delta: Double) {
        val current =
            sheet.value
                ?.weightText
                ?.replace(',', '.')
                ?.toDoubleOrNull()
                ?: uiState.value.entryPrefillKg?.let(activeUnit::fromKilograms)
                ?: return
        sheet.value = sheet.value?.copy(weightText = formatDisplayInput(current + delta))
    }

    private fun save() {
        if (submission.value == WeighInSubmissionState.SAVING) return
        val current = sheet.value ?: return
        val parsed = WeighInInputParser.parse(current.weightText, activeUnit)
        if (parsed.isFailure) {
            submission.value = WeighInSubmissionState.INVALID
            sheet.value = current.copy(weightError = parsed.exceptionOrNull()?.message, saveError = null)
            return
        }
        val whenLogged =
            SheetWhenParser.parse(current.dayText, current.timeText, zoneProvider(), clock.now())
        if (whenLogged == null) {
            submission.value = WeighInSubmissionState.INVALID
            sheet.value =
                current.copy(
                    weightError = null,
                    whenError = "Use YYYY-MM-DD and HH:MM, today or earlier.",
                    saveError = null,
                )
            return
        }
        val (day, at) = whenLogged
        submission.value = WeighInSubmissionState.SAVING
        sheet.value = current.copy(weightError = null, whenError = null, saveError = null)
        viewModelScope.launch {
            val id = profileId ?: profiles.active().getOrNull()?.id
            if (id == null) {
                submission.value = WeighInSubmissionState.FAILURE
                sheet.value = sheet.value?.copy(saveError = "No active profile. Finish setup, then try again.")
                return@launch
            }
            profileId = id
            val outcome =
                weighIns.appendWeighIn(
                    profileId = id,
                    dayEpochDay = day,
                    weightKg = parsed.getOrThrow().kilograms,
                    capturedAt = at,
                    source = MeasurementSource.MANUAL,
                )
            when (outcome) {
                is WloResult.Ok -> {
                    val canonical = weighIns.currentTrend(id, day).getOrNull()
                    submission.value = WeighInSubmissionState.SUCCESS
                    sheet.value = null
                    confirmation.value =
                        WeighInConfirmationUi(
                            eventId = outcome.value.event.id,
                            rawWeightKg = outcome.value.event.valueReal,
                            source = outcome.value.event.source,
                            trend = canonical?.current,
                            delta7 = canonical?.delta7,
                            sampleCount = canonical?.samples?.size ?: 0,
                        )
                    (outcome.value.verdict as? OutlierVerdict.Flagged)?.let { flagged ->
                        verdict.value =
                            VerdictUi(
                                eventId = outcome.value.event.id,
                                weightLabel = activeUnit.formatNumber(outcome.value.event.valueReal),
                                residualLabel = formatResidual(flagged.residualKg),
                            )
                    }
                    refresh()
                }

                is WloResult.Err -> {
                    submission.value = WeighInSubmissionState.FAILURE
                    sheet.value = sheet.value?.copy(saveError = "That didn't save. Nothing changed — try again.")
                }
            }
        }
    }

    /** Latest tuner/window intent wins; stale Room reads never overwrite it. */
    private fun requestPreviewReload(update: () -> Unit) {
        update()
        // Slider/chip controls are state-driven. Reflect the intent before
        // starting I/O so Compose cannot reset the control to stale data.
        data.value =
            data.value.copy(
                window = window.value,
                method = method.value,
                alpha = alpha.value,
            )
        previewReload?.cancel()
        previewReload = viewModelScope.launch { reload(nextGeneration()) }
    }

    /**
     * Tuner changes are pure math over the already-loaded scalar samples.
     * Rendering them synchronously keeps the controlled M3 Slider stable and
     * avoids a database reload race for a computation that needs no I/O.
     */
    private fun renderTunerPreview(update: () -> Unit) {
        update()
        val currentData = data.value
        val currentTrend = currentData.trend
        if (currentTrend == null || currentTrend.samples.isEmpty()) {
            data.value = currentData.copy(method = method.value, alpha = alpha.value)
            return
        }
        val series =
            SmoothingEngine.trend(
                samples =
                    currentTrend.samples.map { point ->
                        EngineWeightSample(point.epochDay, point.value)
                    },
                method = method.value,
                alpha = alpha.value,
            )
        val points = series.points
        val byDay = points.associate { it.epochDay to it.trendKg.value }
        val last = points.last()
        val delta =
            byDay[last.epochDay - DELTA_WINDOW_DAYS]?.let { weekAgo ->
                DerivedValue(
                    last.trendKg.value - weekAgo,
                    Provenance.Derived(
                        formulaVersion = seriesVersion(method.value),
                        inputs = listOf("windowDays=$DELTA_WINDOW_DAYS"),
                    ),
                )
            }
        val monthStart = byDay[last.epochDay - (THIRTY_DAY_WINDOW_DAYS - 1)]
        val delta30 =
            if (monthStart != null) {
                DerivedValue(
                    last.trendKg.value - monthStart,
                    Provenance.Derived(
                        formulaVersion = seriesVersion(method.value),
                        inputs = listOf("windowDays=$THIRTY_DAY_WINDOW_DAYS"),
                    ),
                )
            } else {
                null
            }
        val atDefaults =
            method.value == TrendMethod.EWMA &&
                abs(alpha.value - ConstantsRegistry.EWMA_ALPHA_DEFAULT) < 1e-9
        data.value =
            currentData.copy(
                method = method.value,
                alpha = alpha.value,
                trend =
                    currentTrend.copy(
                        trend =
                            if (currentTrend.trendLineVisible) {
                                points.map { ChartPoint(it.epochDay, it.trendKg.value) }
                            } else {
                                emptyList()
                            },
                        current = last.trendKg,
                        delta7 = delta,
                        delta30 = delta30,
                        preview = !atDefaults,
                    ),
            )
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
                    val refreshed = reload(nextGeneration())
                    if (refreshed) sheet.value = openSheet(prefillKg = null)
                }

                is WloResult.Err -> notice.value = "that didn't delete — nothing changed"
            }
        }
    }

    private fun refresh() {
        previewReload?.cancel()
        val generation = nextGeneration()
        if (data.value.loadState !is WeightLoadState.Content) {
            data.value = data.value.copy(loadState = WeightLoadState.Loading)
        }
        previewReload = viewModelScope.launch { reload(generation) }
    }

    private fun refreshIfBoundaryChanged() {
        val zone = zoneProvider()
        val key = WeightTemporalKey(DayBoundary.epochDay(clock.now(), zone), zone.id)
        if (key != loadedTemporalKey) refresh()
    }

    private fun nextGeneration(): Long = ++loadGeneration

    private suspend fun reload(generation: Long): Boolean {
        val reads = WeightReadAccumulator()
        val profile = reads.value(profiles.active(), null)
        val id = profile?.id
        if (id == null) {
            if (generation != loadGeneration) return false
            data.value =
                if (reads.failed) {
                    data.value.copy(loadState = WeightLoadState.Error(LOAD_FAILED_NOTICE))
                } else {
                    WeighInUiState.LOADING.copy(
                        loadState = WeightLoadState.Empty,
                        massUnit = activeUnit,
                        window = window.value,
                        section = section.value,
                        method = method.value,
                        alpha = alpha.value,
                    )
                }
            profileId = null
            return !reads.failed
        }
        profileId = id
        val unit = activeUnit
        val zone = zoneProvider()
        val today = DayBoundary.epochDay(clock.now(), zone)
        val selectedWindow = window.value
        val from = selectedWindow.days?.let { today - it + 1 } ?: 0L

        val dayEvents = reads.value(weighIns.dayWeighIns(id, today), emptyList())

        // The compressed history (WLO-0055): one row per bucket, coarser with
        // distance — the weight surface's summary of the same store. The
        // verbatim feed is the logbook screen's; the bounded window keeps
        // this read cheap no matter how many years pile up.
        val floor = WeightHistory.floorDay(today, WeightHistory.QUARTERLY_QUARTERS)
        val historyEvents =
            reads.value(
                measurements.rangeOfKind(id, MeasurementKind.WEIGHT, floor, today),
                emptyList(),
            )
        val history =
            historyEvents
                .map { WeightSample(it.dayEpochDay, it.valueReal) }
                .let { WeightHistory.build(it, today) }
                .map { bucket ->
                    HistoryBucketUi(
                        tier = bucket.tier,
                        label = HistoryLabels.bucketLabel(bucket, today),
                        countLabel = "${bucket.count} weigh-in" + if (bucket.count == 1) "" else "s",
                        deltaLabel = bucket.deltaKg?.let { delta -> signedWeight(delta, unit) },
                        weightLabel = bucket.endValueKg?.let(unit::format),
                    )
                }

        // The companion series (WLO-0035 R2): body-fat estimates in % and the
        // waist tape in cm — same store, same window, no cap anywhere.
        val windowEvents = reads.value(measurements.range(id, from, today), emptyList())
        val windowAttrs = reads.value(measurements.attrsInRange(id, from, today), emptyList())
        val waistEventIds =
            windowAttrs
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
            windowAttrs
                .filter { it.attr == "metric" }
                .associate { it.eventId to it.valueText }
        val latestHip =
            windowEvents
                .filter { it.kind == MeasurementKind.CUSTOM && customMetrics[it.id] == "hip" }
                .maxByOrNull { it.capturedAt }
                ?.valueReal

        val samples = reads.value(weighIns.dailyScalars(id, from, today), emptyList())
        val ratios =
            run {
                val waistCm = waistPoints.lastOrNull()?.value
                val heightCm = profile.heightCm
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
        val series = reads.value(weighIns.trend(id, from, today, method.value, alpha.value), null)
        val points = series?.points.orEmpty()
        // THE single trend source (WLO-0030 defect 9): the canonical
        // read — the exact number the Hub shows. The tuner's own output
        // is a preview and is labeled as one.
        val canonical = reads.value(weighIns.currentTrend(id, today), null)
        val goalProgress = goalProgressLoader.load(profile, canonical?.current, today)
        val entryPrefill =
            resolveEntryPrefill(
                trendKg = canonical?.current?.value,
                sampleKg = samples.lastOrNull()?.weightKg,
                historyKg = historyEvents.maxByOrNull { it.capturedAt }?.valueReal,
            )
        val atDefaults =
            method.value == TrendMethod.EWMA &&
                abs(alpha.value - ConstantsRegistry.EWMA_ALPHA_DEFAULT) < 1e-9

        val byDay = points.associate { it.epochDay to it.trendKg.value }
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
        val previewMonthStart =
            lastPoint?.let { point -> byDay[point.epochDay - (THIRTY_DAY_WINDOW_DAYS - 1)] }
        val previewDelta30 =
            if (lastPoint != null && previewMonthStart != null) {
                DerivedValue(
                    lastPoint.trendKg.value - previewMonthStart,
                    Provenance.Derived(
                        formulaVersion = seriesVersion(method.value),
                        inputs = listOf("windowDays=$THIRTY_DAY_WINDOW_DAYS"),
                    ),
                )
            } else {
                null
            }

        val chartWindow =
            resolveChartWindow(selectedWindow, today, from, samples) { candidate ->
                val candidateFrom = candidate.days?.let { today - it + 1 } ?: 0L
                reads.value(weighIns.dailyScalars(id, candidateFrom, today), emptyList())
            }
        val trendVisible = chartWindow.trendVisible
        val stateCopy = chartWindow.stateCopy
        val chartDescription =
            buildChartDescription(
                window = selectedWindow,
                samples = samples,
                unit = unit,
                stateCopy = stateCopy,
            )

        if (generation != loadGeneration) return false
        if (reads.failed) {
            data.value = data.value.copy(loadState = WeightLoadState.Error(LOAD_FAILED_NOTICE))
            return false
        }
        val isEmpty = samples.isEmpty() && history.isEmpty() && bodyFatPoints.isEmpty() && waistPoints.isEmpty()
        data.value =
            WeighInUiState(
                loadState = if (isEmpty) WeightLoadState.Empty else WeightLoadState.Content,
                massUnit = unit,
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
                        current = if (atDefaults) canonical?.current else lastPoint?.trendKg,
                        delta7 = delta,
                        delta30 = if (atDefaults) canonical?.delta30 else previewDelta30,
                        trendLineVisible = trendVisible,
                        preview = !atDefaults,
                        windowStartDay = chartWindow.startDay,
                        windowEndDay = today,
                        alwaysShowTickYear = selectedWindow == ChartWindowUi.Y1 || selectedWindow == ChartWindowUi.ALL,
                        stateCopy = stateCopy,
                        emptyActionWindow = chartWindow.emptyActionWindow,
                        description = chartDescription,
                    ),
                method = method.value,
                alpha = alpha.value,
                lastWeighInLabel =
                    dayEvents.lastOrNull()?.let { event ->
                        "${unit.format(event.valueReal)} · ${timeLabel(event.capturedAt)}"
                    },
                entryPrefillKg = entryPrefill.kilograms,
                entryPrefillContext = entryPrefill.context,
                goalProgress = goalProgress,
            )
        backfillPendingSheet(entryPrefill, unit)
        loadedTemporalKey = WeightTemporalKey(today, zone.id)
        return true
    }

    // --- small helpers ---

    private fun resolveEntryPrefill(
        trendKg: Double?,
        sampleKg: Double?,
        historyKg: Double?,
    ): EntryPrefill {
        if (trendKg != null) return EntryPrefill(trendKg, "Prefilled from your latest trend.")
        val latest = sampleKg ?: historyKg
        if (latest != null) return EntryPrefill(latest, "Prefilled from your latest reliable reading.")
        return EntryPrefill(null, "No previous reading yet.")
    }

    private fun backfillPendingSheet(
        prefill: EntryPrefill,
        unit: MassUnit,
    ) {
        if (!sheetPrefillPending) return
        val openSheet = sheet.value ?: return
        if (openSheet.weightText.isNotBlank()) return
        val kilograms = prefill.kilograms ?: return
        sheet.value =
            openSheet.copy(
                weightText = unit.formatNumber(kilograms),
                prefillContext = prefill.context,
            )
        sheetPrefillPending = false
    }

    private fun timeLabel(instant: Instant): String {
        val local = instant.toLocalDateTime(zoneProvider())
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    private fun signedWeight(
        kg: Double,
        unit: MassUnit,
    ): String {
        val sign = if (kg < 0) "−" else "+"
        return "$sign${unit.formatNumber(abs(kg))} ${unit.symbol}"
    }

    private fun formatDisplayInput(value: Double): String {
        val tenths = (value * 10).roundToLong()
        val whole = tenths / 10
        val tenth = tenths % 10
        return "$whole.$tenth"
    }

    private fun formatResidual(kg: Double): String {
        val sign = if (kg < 0) "−" else "+"
        return "$sign ${activeUnit.formatNumber(abs(kg))} ${activeUnit.symbol}"
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

        /** Trend-line gate (F06 §4: ≥3 points in the selected display window). */
        public const val TREND_GATE_POINTS: Int = 3

        /** Inclusive canonical window used by the neutral 30-day trend change. */
        public const val THIRTY_DAY_WINDOW_DAYS: Long = 30

        /** The headline delta window (F06 §5's weekly rate reads weekly). */
        public const val DELTA_WINDOW_DAYS: Long = 7

        /** The ±0.1 stepper operates in the active display unit. */
        public const val STEP_DISPLAY_UNIT: Double = 0.1

        private const val LOAD_FAILED_NOTICE: String =
            "Weight data couldn't refresh. Your last loaded data is still shown."
    }
}

/** Collects repository failures without publishing default values as a successful empty snapshot. */
private data class ChartWindowState(
    val startDay: Long,
    val trendVisible: Boolean,
    val stateCopy: String?,
    val emptyActionWindow: ChartWindowUi?,
)

private suspend fun resolveChartWindow(
    selectedWindow: ChartWindowUi,
    today: Long,
    from: Long,
    samples: List<EngineWeightSample>,
    loadWindow: suspend (ChartWindowUi) -> List<EngineWeightSample>,
): ChartWindowState {
    var widerSamples: List<EngineWeightSample> = emptyList()
    val widerWindow =
        samples.takeIf { it.isEmpty() }?.let {
            ChartWindowUi.entries
                .dropWhile { candidate -> candidate != selectedWindow }
                .drop(1)
                .firstOrNull { candidate ->
                    widerSamples = loadWindow(candidate)
                    widerSamples.isNotEmpty()
                }
        }
    val stateCopy =
        when {
            samples.isEmpty() -> emptyWindowCopy(selectedWindow, widerSamples)
            samples.size < WeighInViewModel.TREND_GATE_POINTS && samples.last().epochDay < today - 1 ->
                "Last entry ${compactDay(samples.last().epochDay)} — the trend resumes when you do."
            samples.size < WeighInViewModel.TREND_GATE_POINTS -> "Keep weighing — the trend forms in a few days."
            else -> null
        }
    return ChartWindowState(
        startDay = if (selectedWindow == ChartWindowUi.ALL) samples.firstOrNull()?.epochDay ?: today else from,
        trendVisible = samples.size >= WeighInViewModel.TREND_GATE_POINTS,
        stateCopy = stateCopy,
        emptyActionWindow = widerWindow,
    )
}

private fun emptyWindowCopy(
    selectedWindow: ChartWindowUi,
    widerSamples: List<EngineWeightSample>,
): String {
    val windowLabel =
        when (selectedWindow) {
            ChartWindowUi.D30 -> "the last 30 days"
            ChartWindowUi.D90 -> "the last 90 days"
            ChartWindowUi.Y1 -> "the last year"
            ChartWindowUi.ALL -> "your history"
        }
    val widerRange =
        widerSamples
            .takeIf { it.isNotEmpty() }
            ?.let { samples ->
                " · latest entries ${compactDayRange(samples.first().epochDay, samples.last().epochDay)}"
            }.orEmpty()
    return "No weigh-ins in $windowLabel$widerRange"
}

private fun buildChartDescription(
    window: ChartWindowUi,
    samples: List<EngineWeightSample>,
    unit: MassUnit,
    stateCopy: String?,
): String {
    if (samples.isEmpty()) return "${window.label} window. ${stateCopy.orEmpty()}"
    val high = samples.maxOf { it.weightKg }
    val low = samples.minOf { it.weightKg }
    val latest = samples.last().weightKg
    val state = stateCopy ?: "Trend line shown."
    return "${window.label} window, ${samples.size} daily weigh-ins. " +
        "High ${unit.format(high)}, low ${unit.format(low)}, latest ${unit.format(latest)}. $state"
}

private fun compactDayRange(
    firstDay: Long,
    lastDay: Long,
): String {
    val first = LocalDate.fromEpochDays(firstDay)
    val last = LocalDate.fromEpochDays(lastDay)
    return if (firstDay == lastDay) {
        compactDay(firstDay)
    } else if (first.monthNumber == last.monthNumber && first.year == last.year) {
        "${first.dayOfMonth}–${last.dayOfMonth} ${MONTH_LABELS[last.monthNumber - 1]}"
    } else {
        "${compactDay(firstDay)}–${compactDay(lastDay)}"
    }
}

private fun compactDay(epochDay: Long): String {
    val date = LocalDate.fromEpochDays(epochDay)
    return "${date.dayOfMonth} ${MONTH_LABELS[date.monthNumber - 1]}"
}

private val MONTH_LABELS: List<String> =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private class WeightReadAccumulator {
    var failed: Boolean = false
        private set

    fun <T> value(
        result: WloResult<T>,
        fallback: T,
    ): T =
        when (result) {
            is WloResult.Ok -> result.value
            is WloResult.Err -> {
                failed = true
                fallback
            }
        }
}

private data class WeightTemporalKey(
    val epochDay: Long,
    val zoneId: String,
)
