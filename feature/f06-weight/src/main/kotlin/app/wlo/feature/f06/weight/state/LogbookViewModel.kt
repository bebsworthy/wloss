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
import app.wlo.core.data.WeighInAttribute
import app.wlo.core.data.WeighInRepository
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

/** Logbook intents (WLO-0055). */
public sealed interface LogbookEvent {
    /** One verbatim entry goes after its durable recovery receipt is staged. */
    public data class Delete(
        public val eventId: String,
    ) : LogbookEvent

    /** Puts the last deleted weigh-in back (re-appends the snapshot). */
    public data object Undo : LogbookEvent

    /** The snackbar was dismissed or its accessibility-aware timeout elapsed. */
    public data object FinalizeDelete : LogbookEvent

    public data object RetryLoad : LogbookEvent

    public data class Explain(
        public val eventId: String,
    ) : LogbookEvent

    public data class ApplyRange(
        public val range: HistoryRange,
    ) : LogbookEvent

    public data object ClearRange : LogbookEvent

    /** Extends the loaded window one chunk further into the past. */
    public data object LoadEarlier : LogbookEvent

    /** Opens the edit sheet for one entry (F06 §5's inline edit). */
    public data class BeginEdit(
        public val eventId: String,
    ) : LogbookEvent

    public data class EditWeightChange(
        public val text: String,
    ) : LogbookEvent

    /** The edit sheet's date field, ISO YYYY-MM-DD; the past only. */
    public data class EditDayChange(
        public val text: String,
    ) : LogbookEvent

    /** The edit sheet's time field, HH:MM; blank means noon (R-B5). */
    public data class EditTimeChange(
        public val text: String,
    ) : LogbookEvent

    public data object CancelEdit : LogbookEvent

    /** Replaces the entry: append the corrected reading, retire the original. */
    public data object SaveEdit : LogbookEvent
}

/** One verbatim logbook row: a raw weigh-in with its provenance marks (F06 §5). */
public data class LogbookRowUi(
    public val id: String,
    public val dateLabel: String,
    public val timeLabel: String,
    public val weightLabel: String,
    public val sourceLabel: String,
    public val isLowest: Boolean,
    public val flagged: Boolean,
    public val edited: Boolean,
)

/** One month of the logbook — the sticky header carries the month the rows omit. */
public data class LogbookMonthUi(
    public val startDay: Long,
    public val endDay: Long,
    public val headerLabel: String,
    public val statLabel: String?,
    public val rows: List<LogbookRowUi>,
)

/** Half-open history interval shared with the overview and navigation. */
public data class HistoryRange(
    public val startInclusive: Long,
    public val endExclusive: Long,
) {
    init {
        require(startInclusive < endExclusive)
    }
}

public enum class LogbookContentState { Loading, Ready, Empty, FilteredEmpty, Error }

/** The logbook feed's data state (undo/notice are separate flows). */
public data class LogbookUiState(
    public val massUnit: MassUnit,
    public val months: List<LogbookMonthUi>,
    public val totalEntries: Int,
    public val loadedEntries: Int,
    public val contentState: LogbookContentState = LogbookContentState.Loading,
    public val range: HistoryRange? = null,
    public val rangeLabel: String? = null,
) {
    public companion object {
        public val LOADING: LogbookUiState =
            LogbookUiState(
                massUnit = MassUnit.KILOGRAM,
                months = emptyList(),
                totalEntries = 0,
                loadedEntries = 0,
            )
    }
}

/**
 * The just-deleted weigh-in exposed through the Scaffold snackbar host.
 */
public data class DeletedUi(
    public val label: String,
    public val dayEpochDay: Long,
    public val operationId: String,
    public val restoreFailed: Boolean = false,
)

/** The open edit sheet, prefilled from the entry being corrected (F06 §5). */
public data class EditSheetUi(
    public val eventId: String,
    public val weightText: String,
    public val dayText: String,
    public val timeText: String,
    public val weightError: String? = null,
    public val whenError: String? = null,
    public val saveError: String? = null,
    public val isSaving: Boolean = false,
    internal val originalValueKg: Double = Double.NaN,
    internal val originalCapturedAt: Instant? = null,
)

/**
 * The full-page logbook (WLO-0055): every raw weigh-in, newest first, grouped
 * under sticky month headers. The feed is windowed — the latest
 * [WINDOW_MONTHS] months load first and "Load earlier" extends one chunk at a
 * time — so a decade of daily weigh-ins reads the same as a week. The
 * swipe-to-reveal delete and its durable snackbar undo live here, on the
 * verbatim rows; the compressed history card carries no delete.
 */
public class LogbookViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val weighIns: WeighInRepository,
    private val measurements: MeasurementRepository,
    private val settings: SettingsStore,
    private val recovery: LogbookDeletionRecoveryStore? = null,
    initialRange: HistoryRange? = null,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private var profileId: String? = null
    private var activeUnit: MassUnit = MassUnit.KILOGRAM

    /** First day (epoch) of the oldest month in the loaded window. */
    private var windowStart: Long = 0L

    private val data = MutableStateFlow(LogbookUiState.LOADING)
    private val deleted = MutableStateFlow<DeletedUi?>(null)
    private val edit = MutableStateFlow<EditSheetUi?>(null)
    private var pendingDeletion: PendingLogbookDeletion? = null
    private var range: HistoryRange? = initialRange
    private var recoveryReconciled: Boolean = false
    private val notice = MutableStateFlow<String?>(null)

    /** Renderable feed state. */
    public val uiState: StateFlow<LogbookUiState> = data

    /** The last deleted weigh-in, offered back until the window empties. */
    public val deletedState: StateFlow<DeletedUi?> = deleted

    /** The open edit sheet, prefilled from the entry being corrected. */
    public val editState: StateFlow<EditSheetUi?> = edit

    /** Session notices. */
    public val noticeState: StateFlow<String?> = notice

    init {
        windowStart = monthStart(DayBoundary.epochDay(clock.now(), zone)).toEpochDays()
        viewModelScope.launch {
            settings.massUnit.collectLatest { unit ->
                activeUnit = unit
                reload()
            }
        }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: LogbookEvent) {
        when (event) {
            is LogbookEvent.Delete -> deleteWeighIn(event.eventId)
            LogbookEvent.Undo -> undoDelete()
            LogbookEvent.FinalizeDelete -> finalizeDelete()
            LogbookEvent.RetryLoad -> viewModelScope.launch { reload() }
            is LogbookEvent.Explain -> explain(event.eventId)
            is LogbookEvent.ApplyRange -> {
                range = event.range
                viewModelScope.launch { reload() }
            }
            LogbookEvent.ClearRange -> {
                range = null
                viewModelScope.launch { reload() }
            }
            LogbookEvent.LoadEarlier ->
                viewModelScope.launch {
                    val current = LocalDate.fromEpochDays(windowStart)
                    windowStart =
                        LocalDate(current.year, current.monthNumber, 1)
                            .plus(DatePeriod(months = -WINDOW_MONTHS))
                            .toEpochDays()
                    reload()
                }

            is LogbookEvent.BeginEdit -> beginEdit(event.eventId)
            is LogbookEvent.EditWeightChange ->
                edit.value = edit.value?.copy(weightText = event.text, weightError = null, saveError = null)
            is LogbookEvent.EditDayChange ->
                edit.value = edit.value?.copy(dayText = event.text, whenError = null, saveError = null)
            is LogbookEvent.EditTimeChange ->
                edit.value = edit.value?.copy(timeText = event.text, whenError = null, saveError = null)
            LogbookEvent.CancelEdit -> edit.value = null
            LogbookEvent.SaveEdit -> saveEdit()
        }
    }

    private suspend fun reload() {
        if (data.value.months.isEmpty()) data.value = data.value.copy(contentState = LogbookContentState.Loading)
        val active = profileId?.let { null } ?: profiles.active()
        val id = profileId ?: (active as? WloResult.Ok)?.value?.id
        if (id == null) {
            data.value = data.value.copy(contentState = LogbookContentState.Error)
            return
        }
        profileId = id
        if (!recoveryReconciled) {
            recoveryReconciled = true
            recovery?.read(id)?.let { record ->
                if (measurements.byId(record.snapshot.event.id).getOrNull() == null) {
                    pendingDeletion = record
                    deleted.value = record.toUi(activeUnit)
                } else {
                    // The process stopped after staging but before delete.
                    recovery.clear(id)
                }
            }
        }
        val unit = activeUnit
        val today = DayBoundary.epochDay(clock.now(), zone)
        val activeRange = range
        val queryStart = activeRange?.startInclusive ?: windowStart
        val queryEnd = minOf(today, (activeRange?.endExclusive ?: (today + 1)) - 1)

        val attrsResult = measurements.attrsInRange(id, queryStart, queryEnd)
        val eventsResult = measurements.rangeOfKind(id, MeasurementKind.WEIGHT, queryStart, queryEnd)
        if (attrsResult is WloResult.Err || eventsResult is WloResult.Err) {
            data.value = data.value.copy(contentState = LogbookContentState.Error, range = activeRange)
            return
        }
        val flaggedIds =
            (attrsResult as WloResult.Ok)
                .value
                .filter {
                    it.attr == WeighInAttribute.OUTLIER.wireName ||
                        it.attr == WeighInAttribute.EDITED.wireName
                }.toList()
        val flaggedSet = flaggedIds.filter { it.attr == WeighInAttribute.OUTLIER.wireName }.map { it.eventId }.toSet()
        val editedSet = flaggedIds.filter { it.attr == WeighInAttribute.EDITED.wireName }.map { it.eventId }.toSet()
        val events = (eventsResult as WloResult.Ok).value.sortedByDescending { it.capturedAt }
        val total =
            if (activeRange == null) {
                measurements.countOfKind(id, MeasurementKind.WEIGHT, 0, today).getOrNull() ?: events.size
            } else {
                events.size
            }

        // Month groups, newest first; the header earns its keep with the
        // month's facts (weigh-in count + the change across its days'
        // canonical weights), so the rows can stay lean.
        val byDay = events.groupBy { it.dayEpochDay }.toSortedMap(compareByDescending { it })
        val months = mutableListOf<LogbookMonthUi>()
        var monthStartDay = -1L
        var monthDays = mutableListOf<Pair<Long, List<MeasurementEvent>>>()

        fun flushMonth() {
            if (monthDays.isEmpty()) return
            val canonical = monthDays.associate { (day, dayEvents) -> day to dayEvents.minOf { it.valueReal } }
            val newest = canonical.keys.max()
            val oldest = canonical.keys.min()
            val monthBegin = monthStart(oldest).toEpochDays()
            val monthEnd = monthStart(newest).plus(DatePeriod(months = 1)).toEpochDays() - 1
            val count = monthDays.sumOf { (_, dayEvents) -> dayEvents.size }
            val delta = canonical[newest]!! - canonical[oldest]!!
            val statLabel =
                "$count weigh-ins" +
                    if (count > 1 && delta != 0.0) {
                        " · ${if (delta < 0) "−" else "+"}${unit.formatNumber(abs(delta))} ${unit.symbol}"
                    } else {
                        ""
                    }
            months +=
                LogbookMonthUi(
                    startDay = monthBegin,
                    endDay = monthEnd,
                    headerLabel = HistoryLabels.monthHeader(monthBegin),
                    statLabel = statLabel,
                    rows =
                        monthDays.flatMap { (day, dayEvents) ->
                            val lowestId = dayEvents.minByOrNull { it.valueReal }?.id
                            dayEvents.map { event ->
                                LogbookRowUi(
                                    id = event.id,
                                    dateLabel = HistoryLabels.rowDayLabel(day, today),
                                    timeLabel = timeLabel(event.capturedAt),
                                    weightLabel = unit.format(event.valueReal),
                                    sourceLabel = event.source.replace('-', ' '),
                                    isLowest = event.id == lowestId && dayEvents.size > 1,
                                    flagged = event.id in flaggedSet,
                                    edited = event.id in editedSet,
                                )
                            }
                        },
                )
            monthDays = mutableListOf()
        }
        for ((day, dayEvents) in byDay) {
            val dayMonth = monthStart(day).toEpochDays()
            if (dayMonth != monthStartDay) {
                flushMonth()
                monthStartDay = dayMonth
            }
            monthDays += day to dayEvents
        }
        flushMonth()

        data.value =
            LogbookUiState(
                massUnit = unit,
                months = months,
                totalEntries = total,
                loadedEntries = events.size,
                contentState =
                    when {
                        events.isNotEmpty() -> LogbookContentState.Ready
                        activeRange != null -> LogbookContentState.FilteredEmpty
                        else -> LogbookContentState.Empty
                    },
                range = activeRange,
                rangeLabel = activeRange?.let { historyRangeLabel(it) },
            )
    }

    /**
     * F06 §5's inline edit: the sheet opens prefilled from the stored event,
     * the user corrects value and/or when, and saving REPLACES the entry —
     * append the corrected reading, carry the original's sidecar attrs, mark
     * the replacement as edited, retire the original (R-B8: the user's own
     * explicit acts).
     */
    private fun beginEdit(eventId: String) {
        viewModelScope.launch {
            val id = profileId ?: profiles.active().getOrNull()?.id ?: return@launch
            profileId = id
            val event = measurements.byId(eventId).getOrNull() ?: return@launch
            val local = event.capturedAt.toLocalDateTime(zone)
            edit.value =
                EditSheetUi(
                    eventId = event.id,
                    weightText = activeUnit.formatNumber(event.valueReal),
                    dayText = LocalDate.fromEpochDays(event.dayEpochDay).toString(),
                    timeText = timeLabel(event.capturedAt),
                    originalValueKg = event.valueReal,
                    originalCapturedAt = event.capturedAt,
                )
        }
    }

    private fun saveEdit() {
        profileId ?: return
        val current = edit.value ?: return
        if (current.isSaving) return
        val parsedWeight = WeighInInputParser.parse(current.weightText, activeUnit)
        if (parsedWeight.isFailure) {
            edit.value = current.copy(weightError = parsedWeight.exceptionOrNull()?.message, saveError = null)
            return
        }
        edit.value = current.copy(weightError = null, whenError = null, saveError = null, isSaving = true)
        viewModelScope.launch {
            val original = measurements.byId(current.eventId).getOrNull()
            if (original == null) {
                edit.value =
                    edit.value?.copy(
                        saveError = "This entry changed or was removed. Reload the logbook.",
                        isSaving = false,
                    )
                return@launch
            }
            if (original.valueReal != current.originalValueKg || original.capturedAt != current.originalCapturedAt) {
                edit.value =
                    edit.value?.copy(
                        saveError = "This entry changed since editing began. Reload and try again.",
                        isSaving = false,
                    )
                return@launch
            }
            // The sheet deliberately displays minute precision. If the user
            // only corrects the weight, keep the exact stored instant instead
            // of silently truncating seconds and reordering the logbook row.
            val originalDayText = LocalDate.fromEpochDays(original.dayEpochDay).toString()
            val originalTimeText = timeLabel(original.capturedAt)
            val unchangedWhen = current.dayText == originalDayText && current.timeText == originalTimeText
            val parsedWhen =
                if (unchangedWhen) {
                    original.dayEpochDay to original.capturedAt
                } else {
                    SheetWhenParser.parse(current.dayText, current.timeText, zone, clock.now())
                }
            if (parsedWhen == null) {
                edit.value = edit.value?.copy(whenError = "Choose today or an earlier date and time.", isSaving = false)
                return@launch
            }
            val (replacementDay, replacementAt) = parsedWhen
            val replaced =
                weighIns.replaceWeighIn(
                    eventId = original.id,
                    dayEpochDay = replacementDay,
                    weightKg = parsedWeight.getOrThrow().kilograms,
                    capturedAt = replacementAt,
                    editedDescription =
                        "was ${activeUnit.format(original.valueReal)} · " +
                            timeLabel(original.capturedAt),
                )
            when (replaced) {
                is WloResult.Ok -> {
                    edit.value = null
                    reload()
                }

                is WloResult.Err ->
                    edit.value =
                        edit.value?.copy(
                            saveError = "That didn't save — ${replaced.error.debugMessage.lowercase()}",
                            isSaving = false,
                        )
            }
        }
    }

    /**
     * R-B8 amendment (WLO-0035): the user's delete is an explicit act. The
     * snapshot rides in memory for a one-tap undo; the day's trend scalar is
     * the store's to recompute (the door handles it).
     */
    private fun deleteWeighIn(eventId: String) {
        profileId ?: return
        if (pendingDeletion != null) {
            notice.value = "Undo or dismiss the current deletion before deleting another entry."
            return
        }
        viewModelScope.launch {
            val snapshot =
                when (val prepared = weighIns.deletionSnapshot(eventId)) {
                    is WloResult.Ok -> prepared.value
                    is WloResult.Err -> {
                        notice.value = "That didn't delete — nothing changed."
                        return@launch
                    }
                }
            val record =
                PendingLogbookDeletion(
                    operationId = "$eventId:${clock.now().toEpochMilliseconds()}",
                    snapshot = snapshot,
                )
            try {
                recovery?.write(record)
            } catch (_: Throwable) {
                notice.value = "That didn't delete — recovery could not be prepared."
                return@launch
            }
            when (val outcome = weighIns.deleteWeighIn(eventId, clock.now())) {
                is WloResult.Ok -> {
                    val event = outcome.value.event
                    pendingDeletion = record.copy(snapshot = outcome.value)
                    deleted.value =
                        DeletedUi(
                            label = "${activeUnit.format(event.valueReal)} · ${timeLabel(event.capturedAt)}",
                            dayEpochDay = event.dayEpochDay,
                            operationId = record.operationId,
                        )
                    reload()
                }

                is WloResult.Err -> {
                    recovery?.clear(snapshot.event.profileId)
                    notice.value = "That didn't delete — nothing changed."
                }
            }
        }
    }

    private fun undoDelete() {
        profileId ?: return
        val record = pendingDeletion ?: return
        viewModelScope.launch {
            when (weighIns.restoreWeighIn(record.snapshot)) {
                is WloResult.Ok -> {
                    recovery?.clear(record.snapshot.event.profileId)
                    pendingDeletion = null
                    deleted.value = null
                    notice.value = "Weigh-in restored."
                    reload()
                }

                is WloResult.Err -> {
                    deleted.value = deleted.value?.copy(restoreFailed = true)
                    notice.value = "Restore failed. The recovery copy is safe; choose Retry."
                }
            }
        }
    }

    private fun finalizeDelete() {
        val record = pendingDeletion ?: return
        viewModelScope.launch {
            recovery?.clear(record.snapshot.event.profileId)
            pendingDeletion = null
            deleted.value = null
        }
    }

    private fun explain(eventId: String) {
        viewModelScope.launch {
            val event = measurements.byId(eventId).getOrNull() ?: return@launch
            val source = event.source.replace('-', ' ')
            notice.value =
                "${activeUnit.format(event.valueReal)} was recorded from $source " +
                "at ${timeLabel(event.capturedAt)}."
        }
    }

    // --- small helpers ---

    private fun monthStart(day: Long): LocalDate {
        val date = LocalDate.fromEpochDays(day)
        return LocalDate(date.year, date.monthNumber, 1)
    }

    private fun timeLabel(instant: Instant): String {
        val local = instant.toLocalDateTime(zone)
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    private fun historyRangeLabel(value: HistoryRange): String =
        "${LocalDate.fromEpochDays(value.startInclusive)} – ${LocalDate.fromEpochDays(value.endExclusive - 1)}"

    private fun PendingLogbookDeletion.toUi(unit: MassUnit): DeletedUi =
        DeletedUi(
            label = "${unit.format(snapshot.event.valueReal)} · ${timeLabel(snapshot.event.capturedAt)}",
            dayEpochDay = snapshot.event.dayEpochDay,
            operationId = operationId,
        )

    public companion object {
        /** Months in one loaded chunk (WLO-0055's window). */
        public const val WINDOW_MONTHS: Int = 3

        /** Baseline passed to Material's accessibility-aware snackbar timeout. */
        public const val UNDO_WINDOW_MS: Long = 4_500L
    }
}
