package app.wlo.core.common

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class DayBoundaryTest {
    private val utc = TimeZone.UTC

    @Test
    fun dayIdUsesCalendarDayInZone() {
        // 2026-09-12T00:30Z is still Sep 11 in New York.
        val instant = Instant.parse("2026-09-12T00:30:00Z")
        assertEquals("2026-09-12", DayBoundary.dayId(instant, utc))
        assertEquals("2026-09-11", DayBoundary.dayId(instant, TimeZone.of("America/New_York")))
    }

    @Test
    fun startEndOfDayRoundTrip() {
        val start = DayBoundary.startOfDay(20_708, utc) // 2026-09-12
        assertEquals("2026-09-12", DayBoundary.dayId(start, utc))
        assertEquals(20_709, DayBoundary.epochDay(start + 24.hours, utc))
        assertEquals(start + 24.hours - 1.milliseconds, DayBoundary.endOfDay(20_708, utc))
    }

    @Test
    fun noonNormalizationCollapsesTimesWithinOneDay() {
        val zone = TimeZone.of("Europe/Berlin")
        val early = Instant.parse("2026-09-12T04:30:00Z") // 06:30 Berlin
        val late = Instant.parse("2026-09-12T20:30:00Z") // 22:30 Berlin
        assertEquals(DayBoundary.normalizedToNoon(early, zone), DayBoundary.normalizedToNoon(late, zone))
    }
}
