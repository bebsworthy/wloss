package app.wlo.core.common

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

/**
 * Day-boundary math — the single owner (DRY anchor §2.5). A WLO "day" is a
 * calendar day in the user's current zone; all event-level records (R-B8) are
 * keyed by these ids, and day-level rendering never re-implements this math.
 */
public object DayBoundary {
    /** Day id in `YYYY-MM-DD` form (stable sort/render key). */
    public fun dayId(
        instant: Instant,
        timeZone: TimeZone,
    ): String = LocalDate.fromEpochDays(epochDay(instant, timeZone).toInt()).toString()

    /** Epoch-day number for the calendar day containing [instant] in [timeZone]. */
    public fun epochDay(
        instant: Instant,
        timeZone: TimeZone,
    ): Long =
        instant
            .toLocalDateTime(timeZone)
            .date
            .toEpochDays()
            .toLong()

    /** First instant of the calendar day [epochDay] in [timeZone]. */
    public fun startOfDay(
        epochDay: Long,
        timeZone: TimeZone,
    ): Instant = LocalDate.fromEpochDays(epochDay.toInt()).atStartOfDayIn(timeZone)

    /** Last instant (inclusive end = next day start minus 1 ms) of [epochDay] in [timeZone]. */
    public fun endOfDay(
        epochDay: Long,
        timeZone: TimeZone,
    ): Instant = startOfDay(epochDay + 1, timeZone) - 1.milliseconds

    /**
     * Noon normalization: mid-day anchor used by weigh-in trend logic so a 6 a.m.
     * and a 10 p.m. weigh-in of the same calendar day compare as the same point.
     */
    public fun normalizedToNoon(
        instant: Instant,
        timeZone: TimeZone,
    ): Instant {
        val date = instant.toLocalDateTime(timeZone).date
        return date.atTime(12, 0).toInstant(timeZone)
    }
}
