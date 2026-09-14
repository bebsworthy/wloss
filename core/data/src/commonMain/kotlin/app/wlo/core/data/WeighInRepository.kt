package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.common.map
import app.wlo.core.engines.OutlierVerdict
import app.wlo.core.engines.SmoothingEngine
import app.wlo.core.engines.TrendSeries
import app.wlo.core.engines.WeightSample
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.MeasurementAttr
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.model.Provenance
import app.wlo.core.model.TrendMethod
import kotlinx.datetime.Instant

/**
 * The weigh-in door (F06 §3 semantics over the R-B8 event store):
 *  - appends every weigh-in VERBATIM (multiple weigh-ins per day are normal
 *    data — the "post-bathroom win" re-weigh is kept, never collapsed);
 *  - the daily scalar is a DERIVED VIEW: lowest-of-day wins (Happy Scale's
 *    rule — the best estimator of true morning mass);
 *  - trend smoothing lives exactly here (one smoother selection, F06 owns
 *    the math via [SmoothingEngine]; F07 consumes the series per R-B5);
 *  - the ±3σ outlier guard (F06 §4) flags at capture — a one-line "keep or
 *    correct?" confirm in the UI — but NEVER drops or edits the event; the
 *    flag rides the EAV sidecar so the logbook can show it honestly.
 */
public interface WeighInRepository {
    /**
     * Appends the weigh-in, runs the outlier guard against the trailing
     * window, persists the TREND scalar for the day (default smoother), and
     * refreshes the day projection. The verdict informs the UI, never the
     * storage: both branches keep the event.
     */
    public suspend fun appendWeighIn(
        profileId: String,
        dayEpochDay: Long,
        weightKg: Double,
        capturedAt: Instant,
        source: String = MeasurementSource.MANUAL,
        note: String? = null,
    ): WloResult<WeighInOutcome>

    /** Every raw weigh-in of one day, capture order — the time-of-day lens (R-B8). */
    public suspend fun dayWeighIns(
        profileId: String,
        day: Long,
    ): WloResult<List<MeasurementEvent>>

    /** The daily scalar view: lowest-of-day (null when the day has no weigh-in). */
    public suspend fun lowestOfDay(
        profileId: String,
        day: Long,
    ): WloResult<MeasurementEvent?>

    /** Lowest-of-day series per calendar day — the trend/engine input (R-B5). */
    public suspend fun dailyScalars(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<WeightSample>>

    /** The selected smoother over the daily scalars of the window. */
    public suspend fun trend(
        profileId: String,
        fromDay: Long,
        toDay: Long,
        method: TrendMethod = TrendMethod.EWMA,
        alpha: Double = ConstantsRegistry.EWMA_ALPHA_DEFAULT,
    ): WloResult<app.wlo.core.engines.TrendSeries>

    /**
     * THE one current-trend answer (owner review WLO-0030, defect 9: the Hub
     * read the persisted day projection while the weight page recomputed over
     * its own window — two sources of truth). Default smoother (EWMA, R-A2 α)
     * over the canonical [RoomWeighInRepository.TREND_WINDOW_DAYS]-day window
     * ending [toDay]. The Hub hero, the F06 default view, AND the projection
     * writer (the persisted TREND scalar) all go through this one function, so
     * every surface's inputs are identical by construction. [TrendUi]-style
     * tuner selections are F06's preview, never this door.
     */
    public suspend fun currentTrend(
        profileId: String,
        toDay: Long,
    ): WloResult<CurrentTrend>
}

/**
 * The shared current-trend snapshot: the canonical series for a sparkline, the
 * latest smoothed value, and the weekly delta — all from the one window.
 */
public data class CurrentTrend(
    /** Lowest-of-day scalars of the canonical window, oldest first. */
    public val samples: List<WeightSample>,
    /** The default smoother's series over those samples (null when empty). */
    public val series: TrendSeries?,
    /** The latest trend value (null while no weigh-ins exist in the window). */
    public val current: DerivedValue<Double>?,
    /** Trend now minus trend 7 days back (null when the lookback point is absent). */
    public val delta7: DerivedValue<Double>?,
)

/** Append result: the stored event plus the guard's verdict (UI confirm input). */
public data class WeighInOutcome(
    public val event: MeasurementEvent,
    public val verdict: OutlierVerdict,
)

/** Trailing window the outlier guard and default trend recompute look back over. */
internal const val WEIGH_IN_TRAILING_DAYS: Long = 14

public class RoomWeighInRepository public constructor(
    private val measurements: MeasurementRepository,
) : WeighInRepository {
    override suspend fun appendWeighIn(
        profileId: String,
        dayEpochDay: Long,
        weightKg: Double,
        capturedAt: Instant,
        source: String,
        note: String?,
    ): WloResult<WeighInOutcome> {
        // Guard window: the trailing days BEFORE the new event (the candidate
        // is never part of its own σ).
        val recent =
            measurements
                .range(profileId, dayEpochDay - WEIGH_IN_TRAILING_DAYS, dayEpochDay - 1)
                .getOrNull()
                ?.filter { it.kind == MeasurementKind.WEIGHT }
                ?.groupBy { it.dayEpochDay }
                ?.map { (_, events) -> events.minOf { it.valueReal } }
                ?: emptyList()
        val verdict = SmoothingEngine.outlierVerdict(weightKg, recent)

        val appended =
            measurements.append(
                NewMeasurement(
                    profileId = profileId,
                    dayEpochDay = dayEpochDay,
                    kind = MeasurementKind.WEIGHT,
                    valueReal = weightKg,
                    source = source,
                    capturedAt = capturedAt,
                    note = note,
                ),
            )
        val event =
            when (appended) {
                is WloResult.Ok -> appended.value
                is WloResult.Err -> return WloResult.err(appended.error)
            }

        if (verdict is OutlierVerdict.Flagged) {
            // R-B8: the flag is metadata on the kept event, not a verdict on
            // the person — the logbook shows it, projections still count it.
            measurements.attachAttrs(
                event.id,
                listOf(
                    MeasurementAttr(
                        eventId = event.id,
                        attr = OUTLIER_ATTR,
                        valueText = "flagged",
                        valueReal = verdict.residualKg,
                    ),
                ),
            )
        }

        // Persist today's trend scalar (the canonical current-trend answer —
        // the same computation [currentTrend] serves) so the day view and F07's
        // contract (R-B5) read one consistent series. The last TREND event of a
        // day wins in the projection — recompute-safe, append-only.
        currentTrend(profileId, dayEpochDay)
            .getOrNull()
            ?.current
            ?.let { current ->
                measurements.append(
                    NewMeasurement(
                        profileId = profileId,
                        dayEpochDay = dayEpochDay,
                        kind = MeasurementKind.TREND,
                        valueReal = current.value,
                        source = MeasurementSource.ENGINE,
                        capturedAt = capturedAt,
                        note = current.provenance.toString(),
                    ),
                )
            }

        return WloResult.ok(WeighInOutcome(event, verdict))
    }

    override suspend fun dayWeighIns(
        profileId: String,
        day: Long,
    ): WloResult<List<MeasurementEvent>> =
        measurements
            .range(profileId, day, day)
            .map { events -> events.filter { it.kind == MeasurementKind.WEIGHT } }

    override suspend fun lowestOfDay(
        profileId: String,
        day: Long,
    ): WloResult<MeasurementEvent?> =
        measurements
            .range(profileId, day, day)
            .map { events ->
                events
                    .filter { it.kind == MeasurementKind.WEIGHT }
                    .minByOrNull { it.valueReal }
            }

    override suspend fun dailyScalars(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<WeightSample>> =
        measurements.range(profileId, fromDay, toDay).map { events ->
            events
                .filter { it.kind == MeasurementKind.WEIGHT }
                .groupBy { it.dayEpochDay }
                .map { (day, dayEvents) -> WeightSample(day, dayEvents.minOf { it.valueReal }) }
                .sortedBy { it.epochDay }
        }

    override suspend fun trend(
        profileId: String,
        fromDay: Long,
        toDay: Long,
        method: TrendMethod,
        alpha: Double,
    ): WloResult<app.wlo.core.engines.TrendSeries> =
        dailyScalars(profileId, fromDay, toDay).map { samples ->
            SmoothingEngine.trend(samples, method, alpha)
        }

    override suspend fun currentTrend(
        profileId: String,
        toDay: Long,
    ): WloResult<CurrentTrend> =
        dailyScalars(profileId, toDay - TREND_WINDOW_DAYS + 1, toDay).map { samples ->
            if (samples.isEmpty()) {
                CurrentTrend(samples = samples, series = null, current = null, delta7 = null)
            } else {
                val series = SmoothingEngine.trend(samples)
                val last = series.points.last()
                val byDay = series.points.associate { it.epochDay to it.trendKg.value }
                val delta =
                    byDay[last.epochDay - DELTA_WINDOW_DAYS]?.let { weekAgo ->
                        DerivedValue(
                            last.trendKg.value - weekAgo,
                            Provenance.Derived(
                                formulaVersion = seriesFormulaVersion(series),
                                inputs = listOf("windowDays=$DELTA_WINDOW_DAYS"),
                            ),
                        )
                    }
                CurrentTrend(
                    samples = samples,
                    series = series,
                    current = last.trendKg,
                    delta7 = delta,
                )
            }
        }

    /** The series' formula version from its points' provenance (EWMA default). */
    private fun seriesFormulaVersion(series: app.wlo.core.engines.TrendSeries): String =
        (
            series.points
                .lastOrNull()
                ?.trendKg
                ?.provenance as? Provenance.Derived
        )?.formulaVersion
            ?: ConstantsRegistry.EWMA_FORMULA_VERSION

    public companion object {
        /** EAV attr key carrying the outlier flag (F06 §4, kept-verbatim rule). */
        public const val OUTLIER_ATTR: String = "outlier"

        /**
         * THE canonical trend window (days of daily scalars): [currentTrend]
         * computes over it and the append path persists its answer — one
         * window, one answer, everywhere.
         */
        public const val TREND_WINDOW_DAYS: Long = 30

        /** The weekly delta lookback (days) behind the last trend point. */
        public const val DELTA_WINDOW_DAYS: Long = 7
    }
}
