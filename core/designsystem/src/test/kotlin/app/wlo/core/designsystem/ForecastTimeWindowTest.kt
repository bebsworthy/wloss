package app.wlo.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pure forecast-chart geometry (WLO-0034 work item C): ONE shared time
 * window for every band and tick, and the month-tick selection along the
 * bottom. Epoch days in, fractions out — no Compose, hand-checkable.
 */
class ForecastTimeWindowTest {
    private fun bands(
        startEpochDay: Long = day(2026, 9, 8),
        points: Int = 260,
        optimisticFinish: Long? = null,
        expectedFinish: Long? = null,
        pessimisticFinish: Long? = null,
    ): WloForecastBands =
        WloForecastBands(
            startWeightKg = 82.0,
            goalWeightKg = 74.0,
            startEpochDay = startEpochDay,
            optimisticKg = List(points) { 80.0 },
            expectedKg = List(points) { 81.0 },
            pessimisticKg = List(points) { 82.0 },
            optimisticFinishEpochDay = optimisticFinish,
            expectedFinishEpochDay = expectedFinish,
            pessimisticFinishEpochDay = pessimisticFinish,
        )

    @Test
    fun developingForecast_exposesOnlyNeutralOuterRangeCopy() {
        val start = day(2026, 9, 8)
        val expected = start + 80
        val developing =
            bands(
                startEpochDay = start,
                optimisticFinish = start + 60,
                expectedFinish = expected,
                pessimisticFinish = start + 110,
            ).copy(pointDateEligible = false)

        val copy = assertNotNull(provisionalRangeCopy(developing))
        assertTrue("provisional range" in copy.qualifier)
        assertTrue(formatDay(expected) !in copy.range, "central date must not appear in developing copy")
        assertNull(provisionalRangeCopy(developing.copy(pointDateEligible = true)))
    }

    @Test
    fun reachedPath_closesAtTheLastArrival_notAtTheCap() {
        val start = day(2026, 9, 8)
        val window =
            forecastTimeWindow(
                bands(
                    startEpochDay = start,
                    points = 260,
                    optimisticFinish = start + 226,
                    expectedFinish = start + 241,
                    pessimisticFinish = start + 272,
                ),
            )
        // The pessimistic arrival is the fan's right edge — however far out.
        assertEquals(start + 272, window.endEpochDay)
    }

    @Test
    fun unreachedPath_capsAtTwoYears_soTheTimeframeReads() {
        val start = day(2026, 9, 8)
        val window = forecastTimeWindow(bands(startEpochDay = start, points = 260))
        assertEquals(start + FORECAST_DISPLAY_CAP_WEEKS * FORECAST_STEP_DAYS, window.endEpochDay)
    }

    @Test
    fun unreachedShortPath_closesAtItsLastPoint() {
        val start = day(2026, 9, 8)
        val window = forecastTimeWindow(bands(startEpochDay = start, points = 12))
        assertEquals(start + 12 * FORECAST_STEP_DAYS, window.endEpochDay)
    }

    @Test
    fun degenerateBands_stayNonDegenerate() {
        val start = day(2026, 9, 8)
        val window =
            forecastTimeWindow(
                bands(
                    startEpochDay = start,
                    points = 0,
                    optimisticFinish = null,
                    expectedFinish = null,
                    pessimisticFinish = null,
                ),
            )
        // An empty trajectory still spans one engine step (never inverted).
        assertEquals(start + FORECAST_STEP_DAYS, window.endEpochDay)
    }

    @Test
    fun fractions_runZeroToOne_overTheSharedWindow() {
        val start = day(2026, 9, 8)
        val window =
            forecastTimeWindow(
                bands(
                    startEpochDay = start,
                    points = 104,
                    expectedFinish = start + 104 * FORECAST_STEP_DAYS,
                ),
            )
        assertEquals(0f, window.fractionAt(0))
        assertEquals(1f, window.fractionAt(104))
        assertEquals(0.5f, window.fractionAt(52))
        assertEquals(0f, window.fraction(start))
        assertEquals(1f, window.fraction(window.endEpochDay))
    }

    @Test
    fun monthTicks_spanTheWindow_atChartChromeCount() {
        val start = day(2026, 9, 8)
        val end = start + 240 // ~8 months, the mock's Sep ’26 → May ’27 span
        val ticks = forecastMonthTicks(start, end)
        assertEquals(4, ticks.size)
        assertEquals(start, ticks.first())
        assertEquals(end, ticks.last())
        // Even spacing.
        assertEquals((end - start) / 3.0, (ticks[1] - start).toDouble(), 0.5)
        assertEquals((end - start) * 2.0 / 3.0, (ticks[2] - start).toDouble(), 0.5)
    }

    @Test
    fun shortWindows_dropToThreeTicks_soLabelsNeverCollide() {
        val start = day(2026, 9, 8)
        val ticks = forecastMonthTicks(start, start + 56) // 8 weeks
        assertEquals(3, ticks.size)
        assertEquals(start, ticks.first())
        assertEquals(start + 56, ticks.last())
    }

    @Test
    fun monthTickLabel_matchesTheMockChrome() {
        assertEquals("Sep ’26", formatMonthTick(day(2026, 9, 8)))
        assertEquals("Jan ’27", formatMonthTick(day(2027, 1, 20)))
    }

    @Test
    fun dayLabel_keepsTheArrivalRowFormat() {
        assertEquals("May 6", formatDay(day(2027, 5, 6)))
    }

    @Test
    fun horizonWords_rendersTheEngineHorizonInHumanTerms() {
        val start = day(2026, 9, 8)
        assertEquals("5 years", horizonWords(bands(startEpochDay = start, points = 260)))
        assertEquals("~0.5 years", horizonWords(bands(startEpochDay = start, points = 26)))
    }

    @Test
    fun kgPerWeek_matchesTheHouseFormat() {
        assertEquals("0.46 kg/wk", formatKgPerWeek(0.4567))
        assertEquals("0.21 kg/wk", formatKgPerWeek(0.2074))
    }

    @Test
    fun windowHelper_neverProducesAnInvertedSpan() {
        val start = day(2026, 9, 8)
        val window =
            forecastTimeWindow(bands(startEpochDay = start, points = 2, expectedFinish = start))
        assertTrue(window.endEpochDay > window.startEpochDay)
    }

    private fun day(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        val date = java.time.LocalDate.of(year, month, day)
        return date.toEpochDay()
    }
}
