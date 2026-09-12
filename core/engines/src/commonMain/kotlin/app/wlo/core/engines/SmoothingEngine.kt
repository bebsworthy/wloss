package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Provenance
import app.wlo.core.model.TrendMethod
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * F06 smoothing + outlier guard (F06 §3/§4, R-A2) — pure, deterministic,
 * instant-in/value-out (D7: no clock, no IO, no globals).
 *
 * The smoother selection is the user's (F06 §3); every output value carries
 * its [Provenance.Derived] with the registry's formula version so the
 * "how we got here" sheet and the Algorithms page render exactly what ran.
 * Smoothing lives in exactly one place: F06 computes (this engine), F07
 * consumes the trend series (R-B5) — [EnergyEngine.ewma] delegates here.
 */
public object SmoothingEngine {
    public const val EWMA_VERSION: String = ConstantsRegistry.EWMA_FORMULA_VERSION
    public const val ZERO_PHASE_VERSION: String = ConstantsRegistry.EWMA_ZERO_PHASE_FORMULA_VERSION
    public const val MA7_VERSION: String = ConstantsRegistry.MA7_FORMULA_VERSION
    public const val OUTLIER_VERSION: String = ConstantsRegistry.OUTLIER_FORMULA_VERSION

    /**
     * Trailing EWMA, `s_t = α·x_t + (1−α)·s_{t−1}`, seeded `s_0 = x_0`
     * (R-A2 default α = [ConstantsRegistry.EWMA_ALPHA_DEFAULT]). Past-only;
     * lags a few days; rock-stable. THE single EWMA implementation —
     * [EnergyEngine.ewma] is a delegation kept for its call sites.
     */
    public fun ewma(
        values: List<Double>,
        alpha: Double = ConstantsRegistry.EWMA_ALPHA_DEFAULT,
    ): List<Double> {
        require(alpha in 0.0..1.0) { "alpha must be within [0, 1]" }
        if (values.isEmpty()) return emptyList()
        val out = ArrayList<Double>(values.size)
        var s = values.first()
        values.forEach { x ->
            s = alpha * x + (1.0 - alpha) * s
            out += s
        }
        return out
    }

    /**
     * Zero-phase smoother (F06 §3 option 2): the EWMA run forward, then the
     * same filter run backward over its own output (filtfilt-style double
     * application, same α both passes). Near-zero lag; the honestly documented
     * side effects: recent values revise slightly as new data lands, and
     * during plateaus the backward pass can briefly settle below any achieved
     * weight (F06 §3 — shown as a note in UI, never hidden).
     */
    public fun zeroPhaseEwma(
        values: List<Double>,
        alpha: Double = ConstantsRegistry.EWMA_ALPHA_DEFAULT,
    ): List<Double> {
        val forward = ewma(values, alpha)
        if (forward.size < 2) return forward
        return ewma(forward.asReversed(), alpha).asReversed()
    }

    /**
     * Plain trailing [window]-day moving average (F06 §3 option 3: maximum
     * simplicity, maximum lag). The first `window − 1` points are means over
     * the partial window (documented: the series forms gradually — F06 §4's
     * "keep weighing — trend forms in a few days").
     */
    public fun movingAverage(
        values: List<Double>,
        window: Int = ConstantsRegistry.MA7_WINDOW_DAYS,
    ): List<Double> {
        require(window >= 1) { "window must be >= 1" }
        if (values.isEmpty()) return emptyList()
        val out = ArrayList<Double>(values.size)
        var running = 0.0
        values.forEachIndexed { index, x ->
            running += x
            if (index >= window) running -= values[index - window]
            val count = (index + 1).coerceAtMost(window)
            out += running / count
        }
        return out
    }

    /**
     * The typed door repositories call: daily weight scalars in (F06's
     * lowest-of-day/noon-normalized series, R-B5), smoothed series out with
     * per-point provenance.
     */
    public fun trend(
        samples: List<WeightSample>,
        method: TrendMethod = TrendMethod.EWMA,
        alpha: Double = ConstantsRegistry.EWMA_ALPHA_DEFAULT,
    ): TrendSeries {
        val ordered = samples.sortedBy { it.epochDay }
        val values = ordered.map { it.weightKg }
        val smoothed =
            when (method) {
                TrendMethod.EWMA -> ewma(values, alpha)
                TrendMethod.ZERO_PHASE_EWMA -> zeroPhaseEwma(values, alpha)
                TrendMethod.MOVING_AVERAGE_7D -> movingAverage(values)
            }
        val version =
            when (method) {
                TrendMethod.EWMA -> EWMA_VERSION
                TrendMethod.ZERO_PHASE_EWMA -> ZERO_PHASE_VERSION
                TrendMethod.MOVING_AVERAGE_7D -> MA7_VERSION
            }
        val inputs =
            when (method) {
                TrendMethod.MOVING_AVERAGE_7D ->
                    listOf("windowDays=${ConstantsRegistry.MA7_WINDOW_DAYS}", "n=${values.size}")
                else -> listOf("alpha=$alpha", "n=${values.size}")
            }
        return TrendSeries(
            method = method,
            alpha = alpha,
            points =
                ordered.zip(smoothed) { sample, value ->
                    SmoothedPoint(
                        epochDay = sample.epochDay,
                        trendKg =
                            DerivedValue(
                                value = value,
                                provenance = Provenance.Derived(formulaVersion = version, inputs = inputs),
                            ),
                    )
                },
        )
    }

    /**
     * Weigh-in outlier guard (F06 §4): the candidate is judged against the
     * σ of recent residuals (raw − EWMA trend). ±[ConstantsRegistry.OUTLIER_SIGMA_WEIGHT]σ
     * or beyond → [OutlierVerdict.Flagged] — a one-line confirm at capture;
     * the event is appended verbatim either way (R-B8: never dropped, never
     * overwritten). Fewer than [ConstantsRegistry.OUTLIER_MIN_RECENT_POINTS]
     * recent points, or a degenerate σ of 0 (the same convention as the F07
     * outlier screen), leaves the guard silent ([OutlierVerdict.Quiet]).
     */
    public fun outlierVerdict(
        candidateKg: Double,
        recentKg: List<Double>,
        sigmaMultiple: Double = ConstantsRegistry.OUTLIER_SIGMA_WEIGHT,
    ): OutlierVerdict {
        if (recentKg.size < ConstantsRegistry.OUTLIER_MIN_RECENT_POINTS) {
            return OutlierVerdict.Quiet(residualKg = 0.0, sigma = 0.0)
        }
        val trend = ewma(recentKg)
        val residuals = recentKg.zip(trend) { raw, smooth -> raw - smooth }
        val mean = residuals.average()
        val sigma = sqrt(residuals.sumOf { (it - mean) * (it - mean) } / residuals.size)
        val residual = candidateKg - trend.last()
        if (sigma <= 0.0) return OutlierVerdict.Quiet(residualKg = residual, sigma = 0.0)
        val bound = sigmaMultiple * sigma
        return if (abs(residual) > bound) {
            OutlierVerdict.Flagged(residualKg = residual, boundKg = bound, sigma = sigma)
        } else {
            OutlierVerdict.Quiet(residualKg = residual, sigma = sigma)
        }
    }
}

/** One daily weight scalar (F06 lowest-of-day view — the trend input, R-B5). */
@Serializable
public data class WeightSample(
    public val epochDay: Long,
    public val weightKg: Double,
)

/** One smoothed day: the value and its formula provenance (D6). */
@Serializable
public data class SmoothedPoint(
    public val epochDay: Long,
    public val trendKg: DerivedValue<Double>,
)

/** The smoother's full output: selection, α, and the provenance-chipped series. */
@Serializable
public data class TrendSeries(
    public val method: TrendMethod,
    public val alpha: Double,
    public val points: List<SmoothedPoint>,
)

/**
 * The guard's verdict. [Flagged] is the "4.2 kg above yesterday — keep or
 * correct?" trigger (F06 §4); either way the event is kept verbatim.
 */
@Serializable
public sealed interface OutlierVerdict {
    public val residualKg: Double
    public val sigma: Double

    @Serializable
    public data class Quiet(
        override val residualKg: Double,
        override val sigma: Double,
    ) : OutlierVerdict

    @Serializable
    public data class Flagged(
        override val residualKg: Double,
        /** The σ-multiple bound the residual crossed (|residual| > bound). */
        public val boundKg: Double,
        override val sigma: Double,
    ) : OutlierVerdict
}
