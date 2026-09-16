package app.wlo.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrendChartGeometryTest {
    @Test
    fun quarterUsesCalendarMonthBoundaries() {
        val start =
            java.time.LocalDate
                .of(2026, 6, 19)
                .toEpochDay()
        val end =
            java.time.LocalDate
                .of(2026, 9, 16)
                .toEpochDay()
        assertEquals(
            listOf(7, 8, 9).map {
                java.time.LocalDate
                    .of(2026, it, 1)
                    .toEpochDay()
            },
            weightAxisTicks(start, end).map { it.epochDay },
        )
    }

    @Test
    fun shortWindowPreservesBothEndpointsAndMidpoint() {
        assertEquals(listOf(100L, 114L, 129L), weightAxisTicks(100, 129).map { it.epochDay })
        assertEquals(listOf(100L), weightAxisTicks(100, 100).map { it.epochDay })
    }

    @Test
    fun goalAlwaysFitsTheSameScaleAsWeights() {
        for (goal in listOf(40.0, 74.0, 77.0, 150.0)) {
            val bounds = weightChartBounds(listOf(77.0, 78.3), goal)
            assertTrue(goal > bounds.low && goal < bounds.high)
            assertTrue(77.0 > bounds.low && 78.3 < bounds.high)
        }
    }

    @Test
    fun tapUsesBothCoordinatesAndRejectsEmptySpace() {
        val positions =
            listOf(
                androidx.compose.ui.geometry
                    .Offset(100f, 50f),
                androidx.compose.ui.geometry
                    .Offset(100f, 100f),
            )
        assertEquals(
            1,
            nearestWeightPoint(
                positions,
                androidx.compose.ui.geometry
                    .Offset(100f, 99f),
                24f,
            ),
        )
        assertEquals(
            null,
            nearestWeightPoint(
                positions,
                androidx.compose.ui.geometry
                    .Offset(0f, 99f),
                24f,
            ),
        )
    }

    @Test
    fun `same stale sample keeps its honest position in each selected window`() {
        val today = day(2026, 9, 16)
        val sample = today - 20

        assertEquals(9f / 29f, trendXFraction(sample, today - 29, today), 0.0001f)
        assertEquals(69f / 89f, trendXFraction(sample, today - 89, today), 0.0001f)
        assertEquals(344f / 364f, trendXFraction(sample, today - 364, today), 0.0001f)
        assertEquals(1f, trendXFraction(today, today - 29, today))
    }

    @Test
    fun `axis bounds round outward and never collapse`() {
        assertEquals(TrendAxisBounds(77.5, 78.1), trendAxisBounds(listOf(77.54, 78.01)))
        assertEquals(TrendAxisBounds(79.9, 80.1), trendAxisBounds(listOf(80.0)))
        assertEquals(TrendAxisBounds(74.0, 81.0), trendAxisBounds(listOf(74.2, 80.4)))
    }

    @Test
    fun `corner labels are high and low only with no latest fallback`() {
        val labels = trendAxisLabels(listOf(77.6, 78.0, 77.8)) { "%.1f kg".format(it) }

        assertEquals(listOf("78.0 kg", "77.5 kg"), labels)
        assertTrue("77.8 kg" !in labels)
    }

    @Test
    fun `ticks are sparse ordered and include years for year windows`() {
        val start = day(2025, 9, 17)
        val end = day(2026, 9, 16)
        val ticks = trendAxisTicks(start, end, alwaysShowYear = true)

        assertEquals(7, ticks.size)
        assertEquals(start, ticks.first().epochDay)
        assertEquals(end, ticks.last().epochDay)
        assertTrue(ticks.zipWithNext().all { (left, right) -> left.epochDay < right.epochDay })
        assertTrue(ticks.all { "’" in it.label })
    }

    @Test
    fun `caption frames the selected window and names stale data extent`() {
        val start = day(2026, 8, 18)
        val end = day(2026, 9, 16)

        assertEquals(
            "18 Aug – 16 Sep 2026 · data 20–27 Aug",
            trendWindowCaption(start, end, day(2026, 8, 20), day(2026, 8, 27)),
        )
        assertEquals(
            "18 Aug – 16 Sep 2026",
            trendWindowCaption(start, end, day(2026, 8, 20), end),
        )
        assertEquals(
            "18 Aug – 16 Sep 2026 · data 20 Aug",
            trendWindowCaption(start, end, day(2026, 8, 20), day(2026, 8, 20)),
        )
    }

    @Test
    fun `closest point resolves an equal distance to the earlier sample`() {
        val earlier = ChartPoint(epochDay = 10, value = 80.0)
        val later = ChartPoint(epochDay = 12, value = 79.0)

        assertEquals(earlier, closestTrendPoint(listOf(later, earlier), 11.0))
        assertEquals(later, closestTrendPoint(listOf(later, earlier), 11.6))
        assertEquals(null, closestTrendPoint(emptyList(), 11.0))
    }

    private fun day(
        year: Int,
        month: Int,
        day: Int,
    ): Long =
        java.time.LocalDate
            .of(year, month, day)
            .toEpochDay()
}
