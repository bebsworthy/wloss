package app.wlo.feature.f06.weight.state

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The back-datable pad's parser (F06 §4 / WLO-0035 W2): ISO date, optional
 * HH:MM — blank means noon (the R-B5 normalization); the future is refused.
 */
class SheetWhenParserTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val now = Instant.parse("2026-09-14T10:30:00Z") // 12:30 local

    @Test
    fun parsesDateWithTime() {
        val (day, at) =
            SheetWhenParser.parse("2026-09-12", "08:15", zone, now)!!
        assertEquals(Instant.parse("2026-09-12T06:15:00Z"), at)
        assertEquals(LocalDate.parse("2026-09-12").toEpochDays(), day)
    }

    @Test
    fun blankTimeReadsAsNoon() {
        val (_, at) = SheetWhenParser.parse("2026-09-12", "", zone, now)!!
        assertEquals(12, at.toLocalDateTime(zone).hour)
        assertEquals(0, at.toLocalDateTime(zone).minute)
    }

    @Test
    fun todayWithCurrentTimeIsAccepted() {
        val (day, at) = SheetWhenParser.parse("2026-09-14", "12:00", zone, now)!!
        assertEquals(12, at.toLocalDateTime(zone).hour)
        assertEquals(day, SheetWhenParser.parse("2026-09-14", "12:29", zone, now)!!.first)
    }

    @Test
    fun refusesFutureAndGarbage() {
        assertNull(SheetWhenParser.parse("2026-09-15", "08:00", zone, now), "tomorrow is refused")
        assertNull(SheetWhenParser.parse("2026-09-14", "12:31", zone, now), "later today is refused")
        assertNull(SheetWhenParser.parse("12.09.2026", "", zone, now), "non-ISO date refused")
        assertNull(SheetWhenParser.parse("2026-09-12", "25:00", zone, now), "impossible time refused")
        assertNull(SheetWhenParser.parse("", "", zone, now), "empty date refused")
    }
}
