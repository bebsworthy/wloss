package app.wlo.feature.f06.weight.state

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The compressed history's buckets (WLO-0055): the tiers tile the window
 * without overlap, each day reads as its lowest weigh-in, deltas chain
 * bucket-to-bucket, empty buckets keep their row, and everything older than
 * the quarter cap stays out of the card (the logbook screen's business).
 */
class WeightHistoryTest {
    private val today: Long = LocalDate.parse("2026-09-14").toEpochDays() // a Monday

    private fun day(iso: String): Long = LocalDate.parse(iso).toEpochDays()

    @Test
    fun dailyTierCoversSevenDays_lowestOfTheDayIsTheClosingWeight() {
        val buckets =
            WeightHistory.build(
                samples =
                    listOf(
                        WeightSample(today, 77.6),
                        WeightSample(today, 77.0),
                        WeightSample(today - 1, 76.7),
                    ),
                today = today,
            )
        val todayBucket = buckets.first()
        assertEquals(HistoryTier.DAILY, todayBucket.tier)
        assertEquals(2, todayBucket.count)
        assertEquals(77.0, todayBucket.endValueKg) // lowest-of-day, never the re-weigh
        assertEquals(0.3, todayBucket.deltaKg!!, 1e-9) // vs yesterday's closing 76.7
        assertEquals(HistoryTier.DAILY, buckets[1].tier)
        assertEquals(76.7, buckets[1].endValueKg)
        assertEquals(7, buckets.count { it.tier == HistoryTier.DAILY })
    }

    @Test
    fun tiersFollowTheDailyWindow_weeksThenMonthsThenQuarters() {
        val buckets =
            WeightHistory.build(
                samples =
                    listOf(
                        WeightSample(today, 77.0), // Mon Sep 14
                        WeightSample(day("2026-09-07"), 77.5), // Mon, clipped into weekly alone
                        WeightSample(day("2026-09-03"), 78.0),
                        WeightSample(day("2026-08-20"), 79.0),
                        WeightSample(day("2026-08-05"), 80.0),
                        WeightSample(day("2026-07-04"), 81.0),
                        WeightSample(day("2026-03-15"), 85.0),
                        WeightSample(day("2025-11-01"), 90.0),
                    ),
                today = today,
            )
        // Daily first, then the week holding Sep 7 alone, then full weeks.
        val weeklies = buckets.filter { it.tier == HistoryTier.WEEKLY }
        assertEquals(4, weeklies.size)
        assertEquals(day("2026-09-07"), weeklies[0].startDay)
        assertEquals(day("2026-08-31"), weeklies[1].startDay)
        assertEquals(78.0, weeklies[1].endValueKg) // Sep 3 closes the Aug 31 – Sep 6 week
        // Four months before the week tier: Aug (clipped at the 17th), Jul, Jun, May.
        val monthlies = buckets.filter { it.tier == HistoryTier.MONTHLY }
        assertEquals(4, monthlies.size)
        assertEquals(day("2026-08-01"), monthlies[0].startDay)
        assertEquals(80.0, monthlies[0].endValueKg)
        assertEquals(81.0, monthlies[1].endValueKg) // Jul
        assertNull(monthlies[2].endValueKg) // June, empty — the gap is data
        // Then quarters: Q1 2026 holds Mar 15; Q4 2025 holds Nov 1.
        val quarterlies = buckets.filter { it.tier == HistoryTier.QUARTERLY }
        assertTrue(quarterlies.size >= 5)
        assertEquals(85.0, quarterlies.first { it.startDay == day("2026-01-01") }.endValueKg)
        assertEquals(90.0, quarterlies.first { it.startDay == day("2025-10-01") }.endValueKg)
        // The delta chains across tiers: Aug closes −1.0 vs Jul.
        assertEquals(-1.0, monthlies[0].deltaKg)
    }

    @Test
    fun emptyBucketsKeepTheirRow_asExplicitGaps() {
        val buckets = WeightHistory.build(samples = listOf(WeightSample(today, 77.0)), today = today)
        assertTrue(buckets.size > 1)
        val older = buckets.drop(1)
        assertTrue(older.isNotEmpty())
        for (bucket in older) {
            assertEquals(0, bucket.count)
            assertNull(bucket.endValueKg)
            assertNull(bucket.deltaKg)
        }
    }

    @Test
    fun bucketsTileTheWindowWithoutOverlap() {
        val buckets =
            WeightHistory.build(
                samples =
                    listOf(
                        WeightSample(today, 77.0),
                        WeightSample(today - WeightHistory.DAILY_DAYS, 77.5),
                        WeightSample(today - 60, 79.0),
                        WeightSample(today - 200, 84.0),
                        WeightSample(today - 700, 95.0),
                    ),
                today = today,
            )
        val oldestFirst = buckets.asReversed()
        assertEquals(WeightHistory.floorDay(today, WeightHistory.QUARTERLY_QUARTERS), oldestFirst.first().startDay)
        assertEquals(today, oldestFirst.last().endDay)
        for (i in 1 until oldestFirst.size) {
            assertEquals(oldestFirst[i - 1].endDay + 1, oldestFirst[i].startDay)
        }
    }

    @Test
    fun floorDayStartsTwelveQuartersBack_countingThePartialOne() {
        // Today sits in Q3 2026; counting it, the 12th quarter back is Q4 2023.
        assertEquals(day("2023-10-01"), WeightHistory.floorDay(today, WeightHistory.QUARTERLY_QUARTERS))
    }

    @Test
    fun samplesOlderThanTheFloorNeverReachTheCard() {
        val buckets =
            WeightHistory.build(
                samples =
                    listOf(
                        WeightSample(today, 77.0),
                        WeightSample(day("2020-01-01"), 120.0),
                    ),
                today = today,
            )
        assertTrue(buckets.none { it.endValueKg == 120.0 })
    }

    @Test
    fun labelsAreTierAppropriate() {
        val buckets =
            WeightHistory.build(
                samples =
                    listOf(
                        WeightSample(today, 77.0),
                        WeightSample(day("2026-09-03"), 78.0),
                        WeightSample(day("2026-08-05"), 80.0),
                        WeightSample(day("2026-04-02"), 84.0),
                    ),
                today = today,
            )
        fun label(start: Long): String = HistoryLabels.bucketLabel(buckets.first { it.startDay == start }, today)
        assertEquals("Today", label(today))
        assertEquals("Thu 10", label(day("2026-09-10"))) // inside a daily bucket
        assertEquals("Aug 31 – Sep 6", label(day("2026-08-31")))
        assertEquals("Aug 2026", label(day("2026-08-01")))
        assertEquals("Q2 2026", label(day("2026-04-01")))
        assertEquals("SEPTEMBER 2026", HistoryLabels.monthHeader(day("2026-09-01")))
        assertEquals("Yesterday", HistoryLabels.rowDayLabel(today - 1, today))
        assertEquals("Sun 13", HistoryLabels.rowDayLabel(day("2026-09-13"), today + 1))
    }
}
