package app.wlo.core.data

import androidx.room3.withWriteTransaction
import app.wlo.core.common.AppError
import app.wlo.core.common.WloResult
import app.wlo.core.common.map
import app.wlo.core.database.MeasurementEventAttrEntity
import app.wlo.core.database.MeasurementEventEntity
import app.wlo.core.database.WloDatabase
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
import kotlinx.datetime.TimeZone
import kotlin.uuid.Uuid

/**
 * The weigh-in door (F06 §3 semantics over the R-B8 event store):
 *  - appends every weigh-in VERBATIM (multiple weigh-ins per day are normal
 *    data — the "post-bathroom win" re-weigh is kept, never collapsed);
 *  - the daily scalar is a DERIVED VIEW selected by [DailyWeightPolicy];
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

    /** Legacy compatibility query only; canonical trend consumers use [dailySelections]. */
    public suspend fun lowestOfDay(
        profileId: String,
        day: Long,
    ): WloResult<MeasurementEvent?>

    /** Typed daily selections, including attribution and the fixed policy timezone. */
    public suspend fun dailySelections(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<DailyWeightSelection>> =
        dailyScalars(profileId, fromDay, toDay).map { samples ->
            samples.map {
                DailyWeightSelection(
                    dayEpochDay = it.epochDay,
                    kg = it.weightKg,
                    contributingEventIds = emptyList(),
                    candidateCount = 0,
                    reason = DailyWeightSelectionReason.FALLBACK_MEDIAN,
                    policyVersion = DailyWeightPolicy.VERSION,
                    timeZoneId = "unknown",
                )
            }
        }

    /** Canonical daily scalar series per stored calendar day — the trend/engine input (R-B5). */
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

    /**
     * Hard-deletes a weigh-in (R-B8 amendment, WLO-0035): the event and its
     * sidecar go, the day's persisted TREND scalar recomputes (or drops when
     * the day empties — a stale scalar would keep poisoning the day view and
     * exports), and the projection refreshes. [at] stamps the recomputed
     * scalar, mirroring [appendWeighIn]. Returns the snapshot so the UI can
     * offer an in-memory undo.
     */
    public suspend fun deleteWeighIn(
        eventId: String,
        at: Instant,
    ): WloResult<DeletedWeighIn>

    /** Atomically replaces one user-owned weigh-in and repairs both affected suffixes. */
    public suspend fun replaceWeighIn(
        eventId: String,
        dayEpochDay: Long,
        weightKg: Double,
        capturedAt: Instant,
        editedDescription: String,
    ): WloResult<ReplacedWeighIn>

    /** Atomically restores a delete snapshot for the logbook's in-memory undo. */
    public suspend fun restoreWeighIn(snapshot: DeletedWeighIn): WloResult<MeasurementEvent>
}

/** Stable domain-owned sidecar keys; features do not depend on Room implementation constants. */
public enum class WeighInAttribute(
    public val wireName: String,
) {
    OUTLIER("outlier"),
    EDITED("edited"),
}

/** The undo snapshot: what was deleted, sidecar included. */
public data class DeletedWeighIn(
    public val event: MeasurementEvent,
    public val attrs: List<MeasurementAttr>,
)

public data class ReplacedWeighIn(
    public val original: DeletedWeighIn,
    public val replacement: WeighInOutcome,
)

/**
 * The shared current-trend snapshot: the canonical series for a sparkline, the
 * latest smoothed value, and neutral lookback deltas — all from the one window.
 */
public data class CurrentTrend(
    /** Canonical daily scalars of the canonical window, oldest first. */
    public val samples: List<WeightSample>,
    /** The default smoother's series over those samples (null when empty). */
    public val series: TrendSeries?,
    /** The latest trend value (null while no weigh-ins exist in the window). */
    public val current: DerivedValue<Double>?,
    /** Trend now minus trend 7 days back (null when the lookback point is absent). */
    public val delta7: DerivedValue<Double>?,
    /** Change across the canonical 30-calendar-day window, if that full span exists. */
    public val delta30: DerivedValue<Double>? = null,
    /** Attribution/explainer inputs for every sample. */
    public val selections: List<DailyWeightSelection> = emptyList(),
)

/** Append result: the stored event plus the guard's verdict (UI confirm input). */
public data class WeighInOutcome(
    public val event: MeasurementEvent,
    public val verdict: OutlierVerdict,
)

/** Trailing window the outlier guard and default trend recompute look back over. */
internal const val WEIGH_IN_TRAILING_DAYS: Long = 14

internal enum class WeighInMutationStage {
    RAW_EVENT_WRITTEN,
    ATTRIBUTES_WRITTEN,
    ORIGINAL_DELETED,
    TRENDS_REBUILT,
    PROJECTIONS_REBUILT,
}

public class RoomWeighInRepository internal constructor(
    private val db: WloDatabase,
    private val measurements: MeasurementRepository,
    private val projector: DayProjector,
    private val mutationProbe: suspend (WeighInMutationStage) -> Unit,
) : WeighInRepository {
    public constructor(
        db: WloDatabase,
        measurements: MeasurementRepository,
        projector: DayProjector,
    ) : this(db, measurements, projector, {})

    override suspend fun appendWeighIn(
        profileId: String,
        dayEpochDay: Long,
        weightKg: Double,
        capturedAt: Instant,
        source: String,
        note: String?,
    ): WloResult<WeighInOutcome> {
        invalidWeight(weightKg)?.let { return WloResult.err(it) }
        return weighInMutationGuard("weighIn.append") {
            db.withWriteTransaction {
                val verdict = outlierVerdict(profileId, dayEpochDay, weightKg)
                val entity = weightEntity(profileId, dayEpochDay, weightKg, capturedAt, source, note)
                db.measurementEvents().insert(entity)
                mutationProbe(WeighInMutationStage.RAW_EVENT_WRITTEN)
                writeOutlierAttribute(entity.id, verdict)
                mutationProbe(WeighInMutationStage.ATTRIBUTES_WRITTEN)
                repairTrendSuffix(profileId, dayEpochDay, capturedAt)
                WeighInOutcome(entity.toDomain(), verdict)
            }
        }
    }

    override suspend fun deleteWeighIn(
        eventId: String,
        at: Instant,
    ): WloResult<DeletedWeighIn> =
        weighInMutationGuard("weighIn.delete") {
            db.withWriteTransaction {
                val entity = requireWeightEntity(eventId)
                val attrs = db.measurementEventAttrs().forEvent(eventId).map { it.toDomain() }
                db.measurementEventAttrs().deleteForEvent(eventId)
                db.measurementEvents().deleteById(eventId)
                mutationProbe(WeighInMutationStage.ORIGINAL_DELETED)
                repairTrendSuffix(entity.profileId, entity.dayEpochDay, at)
                DeletedWeighIn(entity.toDomain(), attrs)
            }
        }

    override suspend fun replaceWeighIn(
        eventId: String,
        dayEpochDay: Long,
        weightKg: Double,
        capturedAt: Instant,
        editedDescription: String,
    ): WloResult<ReplacedWeighIn> {
        invalidWeight(weightKg)?.let { return WloResult.err(it) }
        return weighInMutationGuard("weighIn.replace") {
            db.withWriteTransaction {
                val original = requireWeightEntity(eventId)
                val originalAttrs = db.measurementEventAttrs().forEvent(eventId).map { it.toDomain() }
                val verdict = outlierVerdict(original.profileId, dayEpochDay, weightKg, excludingEventId = eventId)
                val replacement =
                    weightEntity(
                        profileId = original.profileId,
                        dayEpochDay = dayEpochDay,
                        weightKg = weightKg,
                        capturedAt = capturedAt,
                        source = original.source,
                        note = original.note,
                    )
                db.measurementEvents().insert(replacement)
                mutationProbe(WeighInMutationStage.RAW_EVENT_WRITTEN)
                val carried =
                    originalAttrs
                        .filterNot {
                            it.attr == WeighInAttribute.OUTLIER.wireName ||
                                it.attr == WeighInAttribute.EDITED.wireName
                        }.map { it.copy(eventId = replacement.id) }
                writeAttributes(
                    carried +
                        MeasurementAttr(
                            eventId = replacement.id,
                            attr = WeighInAttribute.EDITED.wireName,
                            valueText = editedDescription,
                        ),
                )
                writeOutlierAttribute(replacement.id, verdict)
                mutationProbe(WeighInMutationStage.ATTRIBUTES_WRITTEN)
                db.measurementEventAttrs().deleteForEvent(eventId)
                db.measurementEvents().deleteById(eventId)
                mutationProbe(WeighInMutationStage.ORIGINAL_DELETED)
                repairTrendSuffix(original.profileId, minOf(original.dayEpochDay, dayEpochDay), capturedAt)
                ReplacedWeighIn(
                    original = DeletedWeighIn(original.toDomain(), originalAttrs),
                    replacement = WeighInOutcome(replacement.toDomain(), verdict),
                )
            }
        }
    }

    override suspend fun restoreWeighIn(snapshot: DeletedWeighIn): WloResult<MeasurementEvent> {
        invalidWeight(snapshot.event.valueReal)?.let { return WloResult.err(it) }
        if (snapshot.event.kind != MeasurementKind.WEIGHT) {
            return WloResult.err(AppError.InvalidInput("only a weigh-in snapshot can be restored"))
        }
        return weighInMutationGuard("weighIn.restore") {
            db.withWriteTransaction {
                val entity = snapshot.event.toEntity()
                db.measurementEvents().insert(entity)
                mutationProbe(WeighInMutationStage.RAW_EVENT_WRITTEN)
                writeAttributes(snapshot.attrs.map { it.copy(eventId = entity.id) })
                mutationProbe(WeighInMutationStage.ATTRIBUTES_WRITTEN)
                repairTrendSuffix(entity.profileId, entity.dayEpochDay, snapshot.event.capturedAt)
                entity.toDomain()
            }
        }
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
        dailySelections(profileId, fromDay, toDay).map { selections ->
            selections.map { WeightSample(it.dayEpochDay, it.kg) }
        }

    override suspend fun dailySelections(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<DailyWeightSelection>> {
        val policy = policyFor(profileId)
        if (policy is WloResult.Err) return policy
        val timeZoneId = (policy as WloResult.Ok).value
        return measurements.range(profileId, fromDay, toDay).map { events ->
            events
                .filter { it.kind == MeasurementKind.WEIGHT }
                .groupBy { it.dayEpochDay }
                .mapNotNull { (day, dayEvents) -> DailyWeightPolicy.select(day, dayEvents, timeZoneId) }
                .sortedBy { it.dayEpochDay }
        }
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
        dailySelections(profileId, toDay - TREND_WINDOW_DAYS + 1, toDay).map { selections ->
            val samples = selections.map { WeightSample(it.dayEpochDay, it.kg) }
            if (samples.isEmpty()) {
                CurrentTrend(samples = samples, series = null, current = null, delta7 = null, delta30 = null, selections = selections)
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
                val first = series.points.first()
                val delta30 =
                    if (last.epochDay - first.epochDay >= TREND_WINDOW_DAYS - 1) {
                        DerivedValue(
                            last.trendKg.value - first.trendKg.value,
                            Provenance.Derived(
                                formulaVersion = seriesFormulaVersion(series),
                                inputs = listOf("windowDays=$TREND_WINDOW_DAYS"),
                            ),
                        )
                    } else {
                        null
                    }
                CurrentTrend(
                    samples = samples,
                    series = series,
                    current = last.trendKg,
                    delta7 = delta,
                    delta30 = delta30,
                    selections = selections,
                )
            }
        }

    private suspend fun outlierVerdict(
        profileId: String,
        dayEpochDay: Long,
        weightKg: Double,
        excludingEventId: String? = null,
    ): OutlierVerdict {
        val recentEvents =
            db
                .measurementEvents()
                .rangeOfKind(
                    profileId,
                    MeasurementKind.WEIGHT.wireName,
                    dayEpochDay - WEIGH_IN_TRAILING_DAYS,
                    dayEpochDay - 1,
                ).filterNot { it.id == excludingEventId }
                .map { it.toDomain() }
        val timeZoneId = policyForOrThrow(profileId)
        val recent =
            recentEvents
                .groupBy { it.dayEpochDay }
                .mapNotNull { (day, events) -> DailyWeightPolicy.select(day, events, timeZoneId)?.kg }
        return SmoothingEngine.outlierVerdict(weightKg, recent)
    }

    private fun weightEntity(
        profileId: String,
        dayEpochDay: Long,
        weightKg: Double,
        capturedAt: Instant,
        source: String,
        note: String?,
    ): MeasurementEventEntity =
        MeasurementEventEntity(
            id = Uuid.random().toString(),
            profileId = profileId,
            dayEpochDay = dayEpochDay,
            kind = MeasurementKind.WEIGHT.wireName,
            valueReal = weightKg,
            unit = MeasurementKind.WEIGHT.unit,
            source = source,
            capturedAtEpochMs = capturedAt.toEpochMilliseconds(),
            note = note,
        )

    private suspend fun writeOutlierAttribute(
        eventId: String,
        verdict: OutlierVerdict,
    ) {
        if (verdict is OutlierVerdict.Flagged) {
            writeAttributes(
                listOf(
                    MeasurementAttr(
                        eventId = eventId,
                        attr = WeighInAttribute.OUTLIER.wireName,
                        valueText = "flagged",
                        valueReal = verdict.residualKg,
                    ),
                ),
            )
        }
    }

    private suspend fun writeAttributes(attrs: List<MeasurementAttr>) {
        if (attrs.isEmpty()) return
        db.measurementEventAttrs().upsertAll(
            attrs.map { MeasurementEventAttrEntity(it.eventId, it.attr, it.valueText, it.valueReal) },
        )
    }

    private suspend fun requireWeightEntity(eventId: String): MeasurementEventEntity {
        val event =
            db.measurementEvents().byId(eventId)
                ?: throw InvalidWeighInMutation("no measurement event $eventId")
        if (event.kind != MeasurementKind.WEIGHT.wireName) {
            throw InvalidWeighInMutation("event $eventId is a ${event.kind}, not a weigh-in")
        }
        return event
    }

    /**
     * Rebuilds every persisted EWMA point whose input window can have changed,
     * then refreshes the same projection suffix inside the caller's transaction.
     */
    private suspend fun repairTrendSuffix(
        profileId: String,
        fromDay: Long,
        at: Instant,
    ) {
        val dao = db.measurementEvents()
        val timeZoneId = policyForOrThrow(profileId)
        val weights =
            dao.rangeOfKind(
                profileId,
                MeasurementKind.WEIGHT.wireName,
                fromDay - TREND_WINDOW_DAYS + 1,
                Long.MAX_VALUE,
            )
        // Include previously persisted trend days as well as surviving weight
        // days: moving/deleting a day's final raw event must clear that now-
        // empty day's scalar and projection.
        val existingTrendDays =
            dao.rangeOfKind(profileId, MeasurementKind.TREND.wireName, fromDay, Long.MAX_VALUE).map { it.dayEpochDay }
        val affectedDays =
            (listOf(fromDay) + existingTrendDays + weights.map { it.dayEpochDay }.filter { it >= fromDay })
                .distinct()
                .sorted()
        affectedDays.forEach { day ->
            val oldTrendIds =
                dao.rangeOfKind(profileId, MeasurementKind.TREND.wireName, day, day).map { it.id }
            oldTrendIds.forEach { db.measurementEventAttrs().deleteForEvent(it) }
            dao.deleteKindForDay(profileId, day, MeasurementKind.TREND.wireName)

            val selections =
                weights
                    .asSequence()
                    .filter { it.dayEpochDay in (day - TREND_WINDOW_DAYS + 1)..day }
                    .groupBy { it.dayEpochDay }
                    .mapNotNull { (sampleDay, events) ->
                        DailyWeightPolicy.select(sampleDay, events.map { it.toDomain() }, timeZoneId)
                    }.sortedBy { it.dayEpochDay }
                    .toList()
            val samples = selections.map { WeightSample(it.dayEpochDay, it.kg) }
            if (samples.isNotEmpty() && weights.any { it.dayEpochDay == day }) {
                val current =
                    SmoothingEngine
                        .trend(samples)
                        .points
                        .last()
                        .trendKg
                val trendId = Uuid.random().toString()
                dao.insert(
                    MeasurementEventEntity(
                        id = trendId,
                        profileId = profileId,
                        dayEpochDay = day,
                        kind = MeasurementKind.TREND.wireName,
                        valueReal = current.value,
                        unit = MeasurementKind.TREND.unit,
                        source = MeasurementSource.ENGINE,
                        capturedAtEpochMs = at.toEpochMilliseconds(),
                        note = current.provenance.toString(),
                    ),
                )
                val selected = selections.last()
                writeAttributes(
                    listOf(
                        MeasurementAttr(trendId, "dailyPolicyVersion", valueText = selected.policyVersion),
                        MeasurementAttr(trendId, "dailyPolicyTimeZone", valueText = selected.timeZoneId),
                        MeasurementAttr(trendId, "dailySelectionReason", valueText = selected.reason.name),
                        MeasurementAttr(trendId, "dailySourceEventIds", valueText = selected.contributingEventIds.joinToString(",")),
                    ),
                )
            }
        }
        mutationProbe(WeighInMutationStage.TRENDS_REBUILT)
        projector.refresh(profileId, fromDay, affectedDays.last())
        mutationProbe(WeighInMutationStage.PROJECTIONS_REBUILT)
    }

    private fun invalidWeight(weightKg: Double): AppError.InvalidInput? =
        if (!weightKg.isFinite() || weightKg !in MIN_WEIGHT_KG..MAX_WEIGHT_KG) {
            AppError.InvalidInput("weightKg must be finite and between $MIN_WEIGHT_KG and $MAX_WEIGHT_KG kg")
        } else {
            null
        }

    private suspend fun policyFor(profileId: String): WloResult<String> =
        try {
            WloResult.ok(policyForOrThrow(profileId))
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            WloResult.err(AppError.Storage(cause = t, detail = "weighIn.policy"))
        }

    private suspend fun policyForOrThrow(profileId: String): String {
        var profile = db.profiles().byId(profileId) ?: throw InvalidWeighInMutation("no profile $profileId")
        if (profile.weightPolicyTimeZoneId == null) {
            db.profiles().initializeWeightPolicy(
                id = profileId,
                timeZoneId = TimeZone.currentSystemDefault().id,
                version = DailyWeightPolicy.VERSION,
            )
            profile = db.profiles().byId(profileId) ?: throw InvalidWeighInMutation("no profile $profileId")
        }
        if (profile.weightPolicyVersion != DailyWeightPolicy.VERSION) {
            throw InvalidWeighInMutation("unsupported daily weight policy ${profile.weightPolicyVersion}")
        }
        return requireNotNull(profile.weightPolicyTimeZoneId)
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
        /**
         * THE canonical trend window (days of daily scalars): [currentTrend]
         * computes over it and the append path persists its answer — one
         * window, one answer, everywhere.
         */
        public const val TREND_WINDOW_DAYS: Long = 30

        /** The weekly delta lookback (days) behind the last trend point. */
        public const val DELTA_WINDOW_DAYS: Long = 7

        /** Broad adult plausibility rail; display-unit conversion happens before this canonical-kg door. */
        public const val MIN_WEIGHT_KG: Double = 30.0
        public const val MAX_WEIGHT_KG: Double = 300.0
    }
}

private class InvalidWeighInMutation(
    message: String,
) : IllegalArgumentException(message)

private suspend inline fun <T> weighInMutationGuard(
    detail: String,
    block: () -> T,
): WloResult<T> =
    try {
        WloResult.ok(block())
    } catch (invalid: InvalidWeighInMutation) {
        WloResult.err(AppError.InvalidInput(invalid.message ?: detail))
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        WloResult.err(AppError.Storage(cause = t, detail = detail))
    }

private fun MeasurementEventAttrEntity.toDomain(): MeasurementAttr = MeasurementAttr(eventId, attr, valueText, valueReal)

private fun MeasurementEvent.toEntity(): MeasurementEventEntity =
    MeasurementEventEntity(
        id = id,
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        kind = kind.wireName,
        valueReal = valueReal,
        unit = unit,
        source = source,
        capturedAtEpochMs = capturedAt.toEpochMilliseconds(),
        note = note,
    )
