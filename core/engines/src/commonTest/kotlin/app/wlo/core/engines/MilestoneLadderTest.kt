package app.wlo.core.engines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MilestoneLadderTest {
    @Test
    fun `canonical trend completes every crossed rung including skipped rungs`() {
        val rungs =
            MilestoneLadder.loss(
                journeyStartKg = 92.0,
                currentTrendKg = 84.0,
                goalKg = 72.0,
            )

        assertTrue(rungs.size in 4..8)
        assertEquals(2, rungs.count { it.state == MilestoneLadder.State.COMPLETED })
        assertTrue(rungs.last().isGoal)
        assertEquals(72.0, rungs.last().weightKg)
    }

    @Test
    fun `edit delete and backfill are plain recomputation with no fired state`() {
        val beforeEdit = MilestoneLadder.loss(90.0, 83.0, 70.0)
        val afterDelete = MilestoneLadder.loss(90.0, 86.0, 70.0)
        val revisedGoal = MilestoneLadder.loss(90.0, 86.0, 76.0)

        assertTrue(
            beforeEdit.count { it.state == MilestoneLadder.State.COMPLETED } >
                afterDelete.count {
                    it.state == MilestoneLadder.State.COMPLETED
                },
        )
        assertEquals(76.0, revisedGoal.last().weightKg)
        assertTrue(revisedGoal.size in 4..8)
    }

    @Test
    fun `dates require both forecast bands and always form a range`() {
        val fast = band(80.0, 1.0, 16)
        val slow = band(80.0, 0.5, 24)
        val projected = MilestoneLadder.loss(80.0, 80.0, 68.0, fast, slow, 20_000)
        val gated = MilestoneLadder.loss(80.0, 80.0, 68.0)

        projected.mapNotNull { it.rangeEpochDays }.forEach { (early, late) -> assertTrue(early <= late) }
        gated.forEach { assertNull(it.rangeEpochDays) }
    }

    @Test
    fun `non-loss shapes do not produce misleading ladders`() {
        assertTrue(MilestoneLadder.loss(80.0, 80.0, 80.0).isEmpty())
        assertTrue(MilestoneLadder.loss(80.0, 80.0, 85.0).isEmpty())
    }

    private fun band(
        startKg: Double,
        weeklyLossKg: Double,
        weeks: Int,
    ): ForecastBand =
        ForecastBand(
            finishEpochDay = null,
            weeklyRatesKg = List(weeks) { weeklyLossKg },
            trajectoryKg = List(weeks) { index -> startKg - weeklyLossKg * (index + 1) },
        )
}
