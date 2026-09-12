package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit tests for the Transparent-engine primitives (EWMA, closed-form TDEE, states). */
class EnergyEngineTest {
    // --- EWMA (R-A2) ---

    @Test
    fun ewma_constantInputStaysConstant() {
        val out = EnergyEngine.ewma(List(20) { 80.0 })
        out.forEach { assertEquals(80.0, it) }
    }

    @Test
    fun ewma_alphaOneIsIdentity() {
        val input = listOf(90.0, 80.0, 85.0, 70.0)
        val out = EnergyEngine.ewma(input, alpha = 1.0)
        assertEquals(input, out)
    }

    @Test
    fun ewma_defaultAlphaComesFromRegistry() {
        // Second value must equal α·x + (1−α)·s0 with the R-A2 alpha.
        val out = EnergyEngine.ewma(listOf(100.0, 50.0))
        val expected = 0.15 * 50.0 + 0.85 * 100.0
        assertTrue(abs(out[1] - expected) < 1e-12)
        assertEquals(ConstantsRegistry.EWMA_ALPHA_DEFAULT, 0.15)
    }

    @Test
    fun ewma_outputLagsBetweenSeedAndInput() {
        val out = EnergyEngine.ewma(listOf(100.0, 50.0, 50.0, 50.0))
        assertTrue(out[1] < 100.0 && out[1] > 50.0, "smoother must lag, not overshoot")
        assertTrue(out.drop(1).zipWithNext().all { (a, b) -> b < a }, "converging monotonically")
    }

    // --- Closed-form TDEE (R-A1/R-A3, F07 §3) ---

    private fun window(
        intake: Double,
        trendStart: Double,
        trendEnd: Double,
        days: Int = 14,
    ): List<EnergyDay> =
        (0 until days).map { d ->
            EnergyDay(
                epochDay = 20_000L + d,
                intakeKcal = intake,
                status = DayStatus.LOGGED,
                trendWeightKg = trendStart + (trendEnd - trendStart) * d / (days - 1),
            )
        }

    @Test
    fun tdee_flatTrendEqualsAverageIntake() {
        val result = EnergyEngine.measuredTdee(window(intake = 2100.0, trendStart = 84.0, trendEnd = 84.0))
        assertNotNull(result)
        assertEquals(2100.0, result.tdeeKcal)
        assertEquals(0.0, result.weeklyTrendChangeKg)
        assertEquals(14, result.usableDays)
    }

    @Test
    fun tdee_losingWeekPullsTdeeBelowIntake() {
        // −0.7 kg across an 8-day window (7-day span) ⇒ weekly change −0.7
        // ⇒ TDEE = intake + 0.7·7700/7 = intake + 770.
        val result = EnergyEngine.measuredTdee(window(intake = 1900.0, trendStart = 84.0, trendEnd = 83.3, days = 8))
        assertNotNull(result)
        assertEquals(1900.0 + ConstantsRegistry.KCAL_PER_KG_FAT / 7.0 * 0.7, result.tdeeKcal, absoluteTolerance = 1e-9)
    }

    @Test
    fun tdee_gainingWeekPullsTdeeAboveIntake() {
        val result = EnergyEngine.measuredTdee(window(intake = 1900.0, trendStart = 84.0, trendEnd = 84.7))
        assertNotNull(result)
        assertTrue(result.tdeeKcal < 1900.0, "gain must push the solve below intake")
    }

    @Test
    fun tdee_skippedDaysCountAsZeroAndStayUsable() {
        val base = window(intake = 2000.0, trendStart = 84.0, trendEnd = 84.0).toMutableList()
        base[3] = base[3].copy(intakeKcal = null, status = DayStatus.FASTED)
        base[7] = base[7].copy(intakeKcal = null, status = DayStatus.SKIPPED)
        val result = EnergyEngine.measuredTdee(base)
        assertNotNull(result)
        // 12 days at 2000 + 2 days at 0 over 14 days = 12·2000/14.
        assertEquals(12 * 2000.0 / 14, result.avgIntakeKcal, absoluteTolerance = 1e-9)
        assertEquals(14, result.usableDays)
    }

    @Test
    fun tdee_unmarkedGapsAreNotUsable() {
        val base = window(intake = 2000.0, trendStart = 84.0, trendEnd = 84.0).toMutableList()
        base[3] = base[3].copy(intakeKcal = null, status = null, trendWeightKg = null)
        val result = EnergyEngine.measuredTdee(base)
        assertNotNull(result)
        assertEquals(13, result.usableDays)
    }

    @Test
    fun tdee_needsTrendAnchors() {
        val noTrend = window(intake = 2000.0, trendStart = 84.0, trendEnd = 84.0).map { it.copy(trendWeightKg = null) }
        assertNull(EnergyEngine.measuredTdee(noTrend))
        assertNull(EnergyEngine.measuredTdee(emptyList()))
    }

    // --- Data-quality states (F07 §4 frozen table) ---

    private fun pairedDay(epochDay: Long): EnergyDay =
        EnergyDay(epochDay = epochDay, intakeKcal = 2000.0, status = DayStatus.LOGGED, trendWeightKg = 84.0)

    @Test
    fun quality_developingUntilEnoughUsableDays() {
        val few = (0L until 9L).map(::pairedDay)
        val state = EnergyEngine.quality(few, lastEpochDay = 20_100L)
        assertEquals(EngineState.Developing(usableDays = 9), state)
    }

    @Test
    fun quality_updatingWhenHealthy() {
        val healthy = (20_000L until 20_000L + ConstantsRegistry.QUALITY_MIN_USABLE_DAYS).map(::pairedDay)
        assertEquals(EngineState.Updating(usableDays = 10), EnergyEngine.quality(healthy, lastEpochDay = 20_009L))
    }

    @Test
    fun quality_heldOnUnloggedRun() {
        val days =
            buildList {
                (0L until ConstantsRegistry.QUALITY_MIN_USABLE_DAYS).forEach { add(pairedDay(20_000L + it)) }
                (19L until 19L + ConstantsRegistry.HOLD_UNLOGGED_DAYS).forEach { add(EnergyDay(epochDay = 20_000L + it)) }
            }
        val held = EnergyEngine.quality(days, lastEpochDay = 20_021L)
        assertTrue(held is EngineState.Held && held.reason == EngineHoldReason.UNLOGGED_DAYS, "got $held")
    }

    @Test
    fun quality_heldOnWeighGap() {
        val days = (0L until 10L).map { pairedDay(20_000L + it) }
        val held = EnergyEngine.quality(days, lastEpochDay = 20_000L + 9 + ConstantsRegistry.HOLD_WEIGH_GAP_DAYS + 1)
        assertTrue(held is EngineState.Held && held.reason == EngineHoldReason.WEIGH_GAP, "got $held")
    }

    @Test
    fun quality_heldOnUserFlag() {
        val days = (0L until 10L).map { pairedDay(20_000L + it) }
        val held = EnergyEngine.quality(days, lastEpochDay = 20_009L, userFlaggedAtypical = true)
        assertTrue(held is EngineState.Held && held.reason == EngineHoldReason.USER_FLAGGED, "got $held")
    }

    @Test
    fun quality_heldOnOutlier() {
        // One 9,000-kcal day among 17 normal days: z ≈ 4.1 > 3σ (with the
        // frozen 21-day window a lone outlier can exceed 3σ; at n=10 it
        // mathematically cannot — the screen needs the fuller window).
        val days =
            (0L until 17L).map { pairedDay(20_000L + it) } +
                EnergyDay(epochDay = 20_017L, intakeKcal = 9_000.0, status = DayStatus.LOGGED, trendWeightKg = 84.0)
        val held = EnergyEngine.quality(days, lastEpochDay = 20_017L)
        assertTrue(held is EngineState.Held && held.reason == EngineHoldReason.OUTLIER, "got $held")
    }
}
