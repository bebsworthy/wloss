package app.wlo.core.data

import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyWeightPolicyTest {
    private val day = 20_708L

    @Test
    fun `window selects nearest 0700 instead of daily minimum`() {
        val selection =
            DailyWeightPolicy.select(
                day,
                listOf(event("morning", 80.0, "2026-09-12T07:00:00Z"), event("evening", 79.0, "2026-09-12T18:00:00Z")),
                "UTC",
            )!!
        assertEquals(80.0, selection.kg)
        assertEquals(listOf("morning"), selection.contributingEventIds)
        assertEquals(DailyWeightSelectionReason.CONSISTENT_WINDOW, selection.reason)
    }

    @Test
    fun `fallback median attributes one or both middle events and ignores input order`() {
        val two = listOf(event("a", 80.0, "2026-09-12T12:00:00Z"), event("b", 82.0, "2026-09-12T18:00:00Z"))
        val forward = DailyWeightPolicy.select(day, two, "UTC")!!
        val reverse = DailyWeightPolicy.select(day, two.reversed(), "UTC")!!
        assertEquals(81.0, forward.kg)
        assertEquals(listOf("a", "b"), forward.contributingEventIds)
        assertEquals(forward, reverse)

        val three = DailyWeightPolicy.select(day, two + event("c", 90.0, "2026-09-12T20:00:00Z"), "UTC")!!
        assertEquals(82.0, three.kg)
        assertEquals(listOf("b"), three.contributingEventIds)
    }

    @Test
    fun `window boundaries and deterministic ties are half open`() {
        val boundary =
            DailyWeightPolicy.select(
                day,
                listOf(event("included", 80.0, "2026-09-12T05:30:00Z"), event("excluded", 79.0, "2026-09-12T09:30:00Z")),
                "UTC",
            )!!
        assertEquals("included", boundary.contributingEventIds.single())

        val tie =
            DailyWeightPolicy.select(
                day,
                listOf(event("later", 81.0, "2026-09-12T07:15:30Z"), event("earlier", 80.0, "2026-09-12T06:45:00Z")),
                "UTC",
            )!!
        assertEquals("earlier", tie.contributingEventIds.single())
    }

    @Test
    fun `invalid legacy rows are excluded and empty valid day is absent`() {
        val invalid = event("invalid", Double.NaN, "2026-09-12T07:00:00Z")
        assertNull(DailyWeightPolicy.select(day, listOf(invalid), "UTC"))
        val selected =
            DailyWeightPolicy.select(
                day,
                listOf(invalid, event("valid", 80.0, "2026-09-12T07:00:00Z")),
                "UTC",
            )!!
        assertEquals(listOf(DailyWeightExclusion("invalid", "invalid canonical kg")), selected.excluded)
    }

    private fun event(
        id: String,
        kg: Double,
        instant: String,
    ): MeasurementEvent =
        MeasurementEvent(
            id = id,
            profileId = "profile",
            dayEpochDay = day,
            kind = MeasurementKind.WEIGHT,
            valueReal = kg,
            unit = "kg",
            source = MeasurementSource.MANUAL,
            capturedAt = Instant.parse(instant),
        )
}
