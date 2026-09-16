package app.wlo.feature.f06.weight

import app.wlo.feature.f06.weight.state.HistoryRange
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class F06RoutesTest {
    @Test
    fun septemberBucketKeepsItsHalfOpenBoundaries() {
        val start = LocalDate(2026, 9, 1).toEpochDays()
        val end = LocalDate(2026, 10, 1).toEpochDays()

        assertEquals("f06/logbook/$start/$end", F06Routes.logbook(HistoryRange(start, end)))
        assertEquals(F06Routes.LOGBOOK, F06Routes.logbook(null))
    }
}
