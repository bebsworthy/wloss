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
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    /** One verbatim entry goes; the UI keeps an in-memory undo with a timer. */
    public data class Delete(
        public val eventId: String,
    ) : LogbookEvent

    /** Puts the last deleted weigh-in back (re-appends the snapshot). */
    public data object Undo : LogbookEvent

    /** Extends the loaded window one chunk further into the past. */
    public data object LoadEarlier : LogbookEvent
}

/** One verbatim logbook row: a raw weigh-in with its provenance marks (F06 §5). */
public data class LogbookRowUi(
    public val id: String,
    public val dateLabel: String,
    public val timeLabel: String,
    public val weightLabel: String,
    public val isLowest: Boolean,
    public val flagged: Boolean,
)

/** One month of the logbook — the sticky header carries the month the rows omit. */
public data class LogbookMonthUi(
    public val startDay: Long,
    public val endDay: Long,
    public val headerLabel: String,
    public val statLabel: String?,
    public val rows: List<LogbookRowUi>,
)

/** The logbook feed's data state (undo/notice are separate flows). */
public data class LogbookUiState(
    public val months: List<LogbookMonthUi>,
    public val totalEntries: Int,
    public val loadedEntries: Int,
) {
    public companion object {
        public val LOADING: LogbookUiState =
            LogbookUiState(
                months = emptyList(),
                totalEntries = 0,
                loadedEntries = 0,
            )
    }
}

/**
 * The just-deleted weigh-in, offered back inline where the row was (WLO-0050,
 * relocated to this screen by WLO-0055) — never as a page-level banner.
 */
public data class DeletedUi(
    public val label: String,
    public val dayEpochDay: Long,
)

/**
 * The full-page logbook (WLO-0055): every raw weigh-in, newest first, grouped
 * under sticky month headers. The feed is windowed — the latest
 * [WINDOW_MONTHS] months load first and "Load earlier" extends one chunk at a
 * time — so a decade of daily weigh-ins reads the same as a week. The
 * swipe-to-reveal delete with its timed inline undo (WLO-0050) lives here,
 * on the verbatim rows; the compressed history card carries no delete.
 */
public class LogbookViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val weighIns: WeighInRepository,
    private val measurements: MeasurementRepository,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private var profileId: String? = null

    /** First day (epoch) of the oldest month in the loaded window. */
    private var windowStart: Long = 0L

    private val data = MutableStateFlow(LogbookUiState.LOADING)
    private val deleted = MutableStateFlow<DeletedUi?>(null)
    private var undoSnapshot: DeletedWeighIn? = null

    /** Bumped per delete/undo so stale auto-clear timers no-op. */
    private var undoGeneration: Int = 0
    private val notice = MutableStateFlow<String?>(null)

    /** Renderable feed state. */
    public val uiState: StateFlow<LogbookUiState> = data

    /** The last deleted weigh-in, offered back until the window empties. */
    public val deletedState: StateFlow<DeletedUi?> = deleted

    /** Session notices. */
    public val noticeState: StateFlow<String?> = notice

    init {
        viewModelScope.launch {
            windowStart = monthStart(DayBoundary.epochDay(clock.now(), zone)).toEpochDays()
            reload()
        }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: LogbookEvent) {
        when (event) {
            is LogbookEvent.Delete -> deleteWeighIn(event.eventId)
            LogbookEvent.Undo -> undoDelete()
            LogbookEvent.LoadEarlier ->
                viewModelScope.launch {
                    val current = LocalDate.fromEpochDays(windowStart)
                    windowStart =
                        LocalDate(current.year, current.monthNumber, 1)
                            .plus(DatePeriod(months = -WINDOW_MONTHS))
                            .toEpochDays()
                    reload()
                }
        }
    }

    private suspend fun reload() {
        val id = profileId ?: profiles.active().getOrNull()?.id ?: return
        profileId = id
        val unit = MassUnit.KILOGRAM
        val today = DayBoundary.epochDay(clock.now(), zone)

        val flaggedIds =
            measurements
                .attrsInRange(id, windowStart, today)
                .getOrNull()
                .orEmpty()
                .filter { it.attr == RoomWeighInRepository.OUTLIER_ATTR }
                .map { it.eventId }
                .toSet()
        val events =
            measurements
                .rangeOfKind(id, MeasurementKind.WEIGHT, windowStart, today)
                .getOrNull()
                .orEmpty()
                .sortedByDescending { it.capturedAt }
        val total = measurements.countOfKind(id, MeasurementKind.WEIGHT, 0, today).getOrNull() ?: events.size

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
                        " · ${if (delta < 0) "−" else "+"}${formatKg(abs(delta))} kg"
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
                                    isLowest = event.id == lowestId && dayEvents.size > 1,
                                    flagged = event.id in flaggedIds,
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
                months = months,
                totalEntries = total,
                loadedEntries = events.size,
            )
    }

    /**
     * R-B8 amendment (WLO-0035): the user's delete is an explicit act. The
     * snapshot rides in memory for a one-tap undo; the day's trend scalar is
     * the store's to recompute (the door handles it).
     */
    private fun deleteWeighIn(eventId: String) {
        val id = profileId ?: return
        viewModelScope.launch {
            when (val outcome = weighIns.deleteWeighIn(eventId, clock.now())) {
                is WloResult.Ok -> {
                    val event = outcome.value.event
                    undoSnapshot = outcome.value
                    deleted.value =
                        DeletedUi(
                            label = "${MassUnit.KILOGRAM.format(event.valueReal)} · ${timeLabel(event.capturedAt)}",
                            dayEpochDay = event.dayEpochDay,
                        )
                    reload()
                    undoGeneration += 1
                    val generation = undoGeneration
                    delay(UNDO_WINDOW_MS)
                    if (undoGeneration == generation) {
                        undoSnapshot = null
                        deleted.value = null
                    }
                }

                is WloResult.Err -> notice.value = "that didn't delete — nothing changed"
            }
        }
    }

    private fun undoDelete() {
        val id = profileId ?: return
        val snapshot = undoSnapshot ?: return
        undoGeneration += 1 // invalidates the auto-clear timer — the user acted
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

    // --- small helpers ---

    private fun monthStart(day: Long): LocalDate {
        val date = LocalDate.fromEpochDays(day)
        return LocalDate(date.year, date.monthNumber, 1)
    }

    private fun timeLabel(instant: Instant): String {
        val local = instant.toLocalDateTime(zone)
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    private fun formatKg(kg: Double): String {
        val tenths = (kg * 10).toLong()
        return "${tenths / 10}.${tenths % 10}"
    }

    public companion object {
        /** Months in one loaded chunk (WLO-0055's window). */
        public const val WINDOW_MONTHS: Int = 3

        /** The undo notice's visible countdown, then it slides away (WLO-0050). */
        public const val UNDO_WINDOW_MS: Long = 4_500L
    }
}
