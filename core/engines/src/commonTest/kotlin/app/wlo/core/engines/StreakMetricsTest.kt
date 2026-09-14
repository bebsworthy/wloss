package app.wlo.core.engines

import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit tests for the multi-oracle current-streak counter (F11). */
class StreakMetricsTest {
    // --- Anchoring (today vs the intact-morning view) ---

    @Test
    fun currentStreak_emptyHistoryIsZero() {
        assertEquals(0, StreakMetrics.currentStreak(emptySet(), asOfDay = 20_000L))
    }

    @Test
    fun currentStreak_todayCountedAnchorsAtToday() {
        val counted = setOf(19_999L, 20_000L, 20_001L, 20_002L)
        assertEquals(4, StreakMetrics.currentStreak(counted, asOfDay = 20_002L))
    }

    @Test
    fun currentStreak_morningBeforeFirstLogKeepsIntactStreak() {
        // asOfDay not yet counted, yesterday counted: the morning shows the
        // intact run ("streak ticks after the log").
        val counted = setOf(19_999L, 20_000L, 20_001L)
        assertEquals(3, StreakMetrics.currentStreak(counted, asOfDay = 20_002L))
    }

    @Test
    fun currentStreak_missedYesterdayBreaksStreak() {
        // Neither today nor yesterday counted: the last run is dead.
        val counted = setOf(19_998L, 19_999L, 20_000L)
        assertEquals(0, StreakMetrics.currentStreak(counted, asOfDay = 20_002L))
    }

    // --- Gaps and runs ---

    @Test
    fun currentStreak_gapMidRunBreaksTheCount() {
        // Only the consecutive run leading up to asOfDay counts.
        val counted = setOf(20_000L, 20_001L, 20_003L, 20_004L, 20_005L)
        assertEquals(3, StreakMetrics.currentStreak(counted, asOfDay = 20_005L))
    }

    @Test
    fun currentStreak_longConsecutiveRunCountsFully() {
        val counted = (20_000L until 20_045L).toSet() // 45 consecutive days
        assertEquals(45, StreakMetrics.currentStreak(counted, asOfDay = 20_044L))
    }

    @Test
    fun currentStreak_isolatedDayWeeksAgoIsZero() {
        val counted = setOf(19_800L)
        assertEquals(0, StreakMetrics.currentStreak(counted, asOfDay = 20_044L))
    }

    // --- Future-day gate ---

    @Test
    fun currentStreak_futureDaysNeverTickTheStreak() {
        // Days after asOfDay are plans, not history; the walk must not see them.
        val counted = setOf(20_004L, 20_005L, 20_006L)
        assertEquals(2, StreakMetrics.currentStreak(counted, asOfDay = 20_005L))
        // Future-only history ticks nothing.
        val futureOnly = setOf(20_006L, 20_007L)
        assertEquals(0, StreakMetrics.currentStreak(futureOnly, asOfDay = 20_005L))
    }
}
