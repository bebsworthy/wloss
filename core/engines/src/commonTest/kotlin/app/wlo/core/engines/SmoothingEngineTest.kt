package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.Provenance
import app.wlo.core.model.TrendMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Unit tests for the F06 smoothing options + outlier guard (F06 §3/§4, R-A2). */
class SmoothingEngineTest {
    @Test
    fun calendarAverageExcludesOldReadingsAcrossGaps() {
        val series =
            SmoothingEngine.trend(
                listOf(WeightSample(1, 100.0), WeightSample(20, 80.0), WeightSample(26, 82.0), WeightSample(27, 84.0)),
                TrendMethod.MOVING_AVERAGE_7D,
            )
        assertEquals(listOf(100.0, 80.0, 81.0, 83.0), series.points.map { it.trendKg.value })
    }

    // --- EWMA (delegation parity: one implementation, two named doors) ---

    @Test
    fun ewma_matchesTheReferenceImplementation() {
        val input = listOf(80.0, 81.5, 79.8, 84.2, 82.0)
        assertEquals(EnergyEngine.ewma(input), SmoothingEngine.ewma(input))
        assertEquals(
            EnergyEngine.ewma(input, alpha = 0.3),
            SmoothingEngine.ewma(input, alpha = 0.3),
        )
    }

    // --- Zero-phase (forward-backward) ---

    @Test
    fun zeroPhase_constantInputStaysConstant() {
        val out = SmoothingEngine.zeroPhaseEwma(List(30) { 80.0 })
        out.forEach { assertEquals(80.0, it) }
    }

    @Test
    fun zeroPhase_centersTheTransitionOnAStep() {
        // 20 days at 80 then a step to 82: the trailing EWMA crosses the
        // midpoint days AFTER the step (its documented lag); the zero-phase
        // pass crosses AT the step (its documented near-zero lag).
        val input = List(20) { 80.0 } + List(20) { 82.0 }
        val trailing = SmoothingEngine.ewma(input)
        val zeroPhase = SmoothingEngine.zeroPhaseEwma(input)

        fun firstAbove(
            values: List<Double>,
            level: Double,
        ): Int = values.indexOfFirst { it > level }

        val stepIndex = 20
        val zeroPhaseCross = firstAbove(zeroPhase, 81.0)
        val trailingCross = firstAbove(trailing, 81.0)
        assertTrue(
            zeroPhaseCross in (stepIndex - 1)..stepIndex,
            "zero-phase transition must sit at the step, crossed at $zeroPhaseCross",
        )
        assertTrue(
            trailingCross > stepIndex,
            "trailing EWMA must lag the step, crossed at $trailingCross",
        )
    }

    // --- 7-day moving average ---

    @Test
    fun ma7_fullWindowsAreSimpleMeans() {
        val input = List(10) { (it + 1).toDouble() } // 1..10
        val out = SmoothingEngine.movingAverage(input)
        // Day 7 (index 6): mean(1..7) = 4; day 10: mean(4..10) = 7.
        assertEquals(4.0, out[6])
        assertEquals(7.0, out[9])
    }

    @Test
    fun ma7_partialWindowsMeanWhatExists() {
        val out = SmoothingEngine.movingAverage(listOf(70.0, 80.0, 90.0))
        assertEquals(70.0, out[0])
        assertEquals(75.0, out[1])
        assertEquals(80.0, out[2])
    }

    @Test
    fun ma7_windowFromRegistryIsSeven() {
        val input = List(9) { 1.0 }
        assertEquals(ConstantsRegistry.MA7_WINDOW_DAYS, 7)
        assertEquals(9, SmoothingEngine.movingAverage(input).size)
    }

    // --- The typed door: provenance ---

    @Test
    fun trend_stampsRegistryFormulaVersions() {
        val samples =
            listOf(20_704L to 81.0, 20_705L to 81.2, 20_706L to 81.1, 20_707L to 81.4)
                .map { (day, kg) -> WeightSample(day, kg) }

        val ewma = SmoothingEngine.trend(samples, TrendMethod.EWMA)
        assertIs<Provenance.Derived>(
            ewma.points
                .first()
                .trendKg.provenance,
        )
        assertEquals(
            ConstantsRegistry.EWMA_FORMULA_VERSION,
            (
                ewma.points
                    .first()
                    .trendKg.provenance as Provenance.Derived
            ).formulaVersion,
        )

        val zeroPhase = SmoothingEngine.trend(samples, TrendMethod.ZERO_PHASE_EWMA)
        assertEquals(
            ConstantsRegistry.EWMA_ZERO_PHASE_FORMULA_VERSION,
            (
                zeroPhase.points
                    .first()
                    .trendKg.provenance as Provenance.Derived
            ).formulaVersion,
        )

        val ma7 = SmoothingEngine.trend(samples, TrendMethod.MOVING_AVERAGE_7D)
        assertEquals(
            ConstantsRegistry.MA7_FORMULA_VERSION,
            (
                ma7.points
                    .last()
                    .trendKg.provenance as Provenance.Derived
            ).formulaVersion,
        )
    }

    @Test
    fun trend_sortsByEpochDayAndCarriesAlpha() {
        val samples =
            listOf(20_705L to 81.2, 20_704L to 81.0).map { (day, kg) -> WeightSample(day, kg) }
        val series = SmoothingEngine.trend(samples, TrendMethod.EWMA)
        assertEquals(listOf(20_704L, 20_705L), series.points.map { it.epochDay })
        assertEquals(ConstantsRegistry.EWMA_ALPHA_DEFAULT, series.alpha)
    }

    // --- Outlier guard (±3σ, F06 §4) ---

    private val stableHistory: List<Double> =
        listOf(81.0, 81.1, 80.9, 81.05, 80.95, 81.15, 81.0)

    @Test
    fun outlier_guardIsSilentBeforeEnoughHistory() {
        val verdict = SmoothingEngine.outlierVerdict(120.0, stableHistory.take(2))
        assertIs<OutlierVerdict.Quiet>(verdict)
    }

    @Test
    fun outlier_normalFluctuationIsQuiet() {
        val verdict = SmoothingEngine.outlierVerdict(81.2, stableHistory)
        assertIs<OutlierVerdict.Quiet>(verdict)
    }

    @Test
    fun outlier_bigJumpIsFlaggedWithTheSigmaBound() {
        val verdict = SmoothingEngine.outlierVerdict(85.0, stableHistory)
        val flagged = assertIs<OutlierVerdict.Flagged>(verdict)
        assertEquals(ConstantsRegistry.OUTLIER_SIGMA_WEIGHT, 3.0)
        assertTrue(flagged.residualKg > flagged.boundKg, "85 kg on an 81 kg trend must cross the bound")
        assertTrue(flagged.boundKg > 0.0)
    }

    @Test
    fun outlier_bigDropIsFlaggedToo() {
        val verdict = SmoothingEngine.outlierVerdict(76.0, stableHistory)
        assertIs<OutlierVerdict.Flagged>(verdict)
    }

    @Test
    fun outlier_degenerateHistoryStaysQuiet() {
        // Perfectly flat history: σ = 0 — the guard stays silent (same
        // convention as the F07 outlier screen), the event still lands.
        val verdict = SmoothingEngine.outlierVerdict(95.0, List(5) { 80.0 })
        assertIs<OutlierVerdict.Quiet>(verdict)
    }
}
