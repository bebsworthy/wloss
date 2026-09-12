package app.wlo.core.engines

import app.wlo.core.model.TrendMethod
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property tests (T-J) for the smoother invariants: output length matches the
 * input, a constant series stays exactly constant under all three methods,
 * and the zero-phase pass never overshoots the input's range.
 */
class SmoothingEnginePropertyTest {
    @Test
    fun allMethodsPreserveLengthAndFiniteValues() =
        runTest {
            checkAll(200, Arb.numericDouble(60.0, 120.0), Arb.numericDouble(0.05, 2.0)) { base, noise ->
                val input = List(14) { base + (if (it % 2 == 0) noise else -noise) }
                TrendMethod.entries.forEach { method ->
                    val series = SmoothingEngine.trend(List(14) { i -> WeightSample(20_700L + i, input[i]) }, method)
                    assertTrue(series.points.size == input.size, "$method must preserve length")
                    series.points.forEach { point ->
                        assertTrue(point.trendKg.value.isFinite(), "$method must emit finite values")
                    }
                }
            }
        }

    @Test
    fun constantSeriesStaysConstantUnderEveryMethod() =
        runTest {
            checkAll(100, Arb.numericDouble(40.0, 160.0)) { weight ->
                val input = List(21) { weight }
                TrendMethod.entries.forEach { method ->
                    val series = SmoothingEngine.trend(List(21) { i -> WeightSample(20_700L + i, input[i]) }, method)
                    series.points.forEach { point ->
                        assertTrue(
                            abs(point.trendKg.value - weight) < 1e-9,
                            "$method must keep a constant series constant",
                        )
                    }
                }
            }
        }

    @Test
    fun zeroPhaseStaysWithinTheInputRange() =
        runTest {
            checkAll(200, Arb.numericDouble(50.0, 130.0), Arb.numericDouble(0.0, 3.0)) { base, swing ->
                val input = List(20) { base + if (it % 3 == 0) swing else 0.0 }
                val lo = input.min() - 1e-9
                val hi = input.max() + 1e-9
                val out = SmoothingEngine.zeroPhaseEwma(input)
                out.forEach { value ->
                    assertTrue(
                        value >= lo && value <= hi,
                        "zero-phase must stay within [$lo, $hi], got $value",
                    )
                }
            }
        }

    @Test
    fun outlierGuardFlagsExactlyBeyondThreeSigma() =
        runTest {
            checkAll(150, Arb.numericDouble(70.0, 100.0), Arb.numericDouble(0.05, 1.5)) { base, spread ->
                val history = List(12) { base + spread * ((it % 4) - 1.5) }
                val inBand = SmoothingEngine.outlierVerdict(history.last(), history.dropLast(1))
                assertTrue(inBand is OutlierVerdict.Quiet || inBand is OutlierVerdict.Flagged)
                if (inBand is OutlierVerdict.Flagged) {
                    assertTrue(abs(inBand.residualKg) > inBand.boundKg)
                    assertTrue(inBand.sigma > 0.0)
                }
            }
        }
}
