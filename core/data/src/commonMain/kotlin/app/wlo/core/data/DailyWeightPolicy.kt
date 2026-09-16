package app.wlo.core.data

import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

/** The versioned reducer used by every canonical weight-trend consumer. */
public object DailyWeightPolicy {
    public const val VERSION: String = "consistent-window-v1"
    public const val WINDOW_START_MINUTE: Int = 5 * 60 + 30
    public const val WINDOW_END_MINUTE_EXCLUSIVE: Int = 9 * 60 + 30
    public const val TARGET_MINUTE: Int = 7 * 60

    /**
     * Reduces one already-bucketed local day without changing that historical bucket.
     * Capture instants are interpreted in the profile's fixed policy timezone.
     */
    public fun select(
        dayEpochDay: Long,
        events: List<MeasurementEvent>,
        timeZoneId: String,
    ): DailyWeightSelection? {
        val zone = TimeZone.of(timeZoneId)
        val (valid, invalid) =
            events
                .filter { it.dayEpochDay == dayEpochDay }
                .partition {
                    it.kind == MeasurementKind.WEIGHT &&
                        it.unit == MeasurementKind.WEIGHT.unit &&
                        it.valueReal.isFinite() &&
                        it.valueReal > 0.0
                }
        if (valid.isEmpty()) return null

        val inWindow =
            valid.filter { event ->
                val local = event.capturedAt.toLocalDateTime(zone)
                val minute = local.hour * 60 + local.minute
                minute in WINDOW_START_MINUTE until WINDOW_END_MINUTE_EXCLUSIVE
            }
        val excluded = invalid.map { DailyWeightExclusion(it.id, "invalid canonical kg") }
        if (inWindow.isNotEmpty()) {
            val selected =
                inWindow.minWith(
                    compareBy<MeasurementEvent> {
                        val local = it.capturedAt.toLocalDateTime(zone)
                        abs(local.hour * 60 + local.minute - TARGET_MINUTE)
                    }.thenBy {
                        val local = it.capturedAt.toLocalDateTime(zone)
                        local.hour * 60 + local.minute
                    }.thenBy { it.capturedAt }
                        .thenBy { it.id },
                )
            return DailyWeightSelection(
                dayEpochDay = dayEpochDay,
                kg = selected.valueReal,
                contributingEventIds = listOf(selected.id),
                candidateCount = valid.size,
                reason = DailyWeightSelectionReason.CONSISTENT_WINDOW,
                policyVersion = VERSION,
                timeZoneId = timeZoneId,
                excluded = excluded,
            )
        }

        val ordered = valid.sortedWith(compareBy<MeasurementEvent> { it.valueReal }.thenBy { it.capturedAt }.thenBy { it.id })
        val middle = ordered.size / 2
        val contributors = if (ordered.size % 2 == 0) listOf(ordered[middle - 1], ordered[middle]) else listOf(ordered[middle])
        return DailyWeightSelection(
            dayEpochDay = dayEpochDay,
            kg = contributors.sumOf { it.valueReal } / contributors.size,
            contributingEventIds = contributors.map { it.id },
            candidateCount = valid.size,
            reason = DailyWeightSelectionReason.FALLBACK_MEDIAN,
            policyVersion = VERSION,
            timeZoneId = timeZoneId,
            excluded = excluded,
        )
    }
}

public enum class DailyWeightSelectionReason {
    CONSISTENT_WINDOW,
    FALLBACK_MEDIAN,
}

public data class DailyWeightExclusion(
    public val eventId: String,
    public val reason: String,
)

public data class DailyWeightSelection(
    public val dayEpochDay: Long,
    public val kg: Double,
    public val contributingEventIds: List<String>,
    public val candidateCount: Int,
    public val reason: DailyWeightSelectionReason,
    public val policyVersion: String,
    public val timeZoneId: String,
    public val excluded: List<DailyWeightExclusion> = emptyList(),
)
