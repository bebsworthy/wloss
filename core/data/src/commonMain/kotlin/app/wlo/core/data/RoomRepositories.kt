package app.wlo.core.data

import androidx.room3.withWriteTransaction
import app.wlo.core.common.AppError
import app.wlo.core.common.ClockPort
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.database.ConsentLedgerEntity
import app.wlo.core.database.DayRecordEntity
import app.wlo.core.database.MeasurementEventAttrEntity
import app.wlo.core.database.MeasurementEventEntity
import app.wlo.core.database.ProfileEntity
import app.wlo.core.database.ProvenanceEntity
import app.wlo.core.database.TargetsVersionEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.documents.TargetsDocumentIO
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.documents.TargetsWriterId
import app.wlo.core.engines.InputsHash
import app.wlo.core.engines.PlannerEngine
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.MeasurementAttr
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.Profile
import app.wlo.core.model.Provenance
import app.wlo.core.model.Sex
import app.wlo.core.model.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.uuid.Uuid

/**
 * Room-backed implementations of the spine doors (ARCHITECTURE §2.2).
 * Every boundary wraps storage failures into [AppError.Storage] (D8); Flows
 * use `catch` so failures arrive as emitted [WloResult.Err] values.
 */

internal fun ProfileEntity.toDomain(): Profile =
    Profile(
        id = id,
        sex = Sex.fromWireName(sex),
        birthYear = birthYear,
        heightCm = heightCm,
        startWeightKg = startWeightKg,
        activityLevel =
            app.wlo.core.model.ActivityLevel
                .fromWireName(activityLevel)
                ?: app.wlo.core.model.ActivityLevel.SEDENTARY,
        unitPreference = UnitSystem.fromWireName(unitPreference) ?: UnitSystem.METRIC,
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
        archivedAt = archivedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
    )

internal fun MeasurementEventEntity.toDomain(): MeasurementEvent =
    MeasurementEvent(
        id = id,
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        kind = MeasurementKind.fromWireName(kind) ?: MeasurementKind.WEIGHT,
        valueReal = valueReal,
        unit = unit,
        source = source,
        capturedAt = Instant.fromEpochMilliseconds(capturedAtEpochMs),
        note = note,
    )

/** D8 wrapper: storage failures become [AppError.Storage] values, never throws. */
internal inline fun <T> storageGuard(
    detail: String,
    block: () -> T,
): WloResult<T> =
    try {
        WloResult.ok(block())
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        WloResult.err(AppError.Storage(cause = t, detail = detail))
    }

// --- profiles --------------------------------------------------------------

public class RoomProfileRepository public constructor(
    private val db: WloDatabase,
    private val settings: SettingsStore,
) : ProfileRepository {
    private val dao = db.profiles()

    override fun observeActive(): Flow<WloResult<Profile?>> =
        dao
            .observeActive()
            .map { entity -> WloResult.ok(entity?.toDomain()) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "profiles.observeActive"))) }

    override suspend fun active(): WloResult<Profile?> = storageGuard("profiles.active") { dao.active()?.toDomain() }

    override suspend fun byId(profileId: String): WloResult<Profile?> = storageGuard("profiles.byId") { dao.byId(profileId)?.toDomain() }

    override suspend fun create(
        profile: NewProfile,
        at: Instant,
    ): WloResult<Profile> =
        storageGuard("profiles.create") {
            val entity =
                ProfileEntity(
                    id = Uuid.random().toString(),
                    sex = profile.sex?.wireName,
                    birthYear = profile.birthYear,
                    heightCm = profile.heightCm,
                    startWeightKg = profile.startWeightKg,
                    activityLevel = profile.activityLevel.wireName,
                    unitPreference = profile.unitPreference.wireName,
                    createdAtEpochMs = at.toEpochMilliseconds(),
                )
            dao.upsert(entity)
            // Keep the app-level unit setting in lockstep (R-D10 single
            // source for rendering; the profile row is the partition-ready copy).
            settings.setMassUnit(unitFor(profile.unitPreference))
            entity.toDomain()
        }

    override suspend fun archive(
        profileId: String,
        at: Instant,
    ): WloResult<Unit> = storageGuard("profiles.archive") { dao.archive(profileId, at.toEpochMilliseconds()) }

    override suspend fun setUnitPreference(
        profileId: String,
        unit: UnitSystem,
    ): WloResult<Unit> =
        storageGuard("profiles.setUnitPreference") {
            dao.setUnitPreference(profileId, unit.wireName)
            settings.setMassUnit(unitFor(unit))
        }

    private fun unitFor(unit: UnitSystem): MassUnit =
        when (unit) {
            UnitSystem.METRIC -> MassUnit.KILOGRAM
            UnitSystem.IMPERIAL -> MassUnit.POUND
        }
}

// --- measurement events (R-B8) ---------------------------------------------

public class RoomMeasurementRepository public constructor(
    private val db: WloDatabase,
    private val projector: DayProjector,
) : MeasurementRepository {
    private val dao = db.measurementEvents()
    private val attrDao = db.measurementEventAttrs()

    override suspend fun append(event: NewMeasurement): WloResult<MeasurementEvent> =
        storageGuard("measurement.append") {
            val entity =
                MeasurementEventEntity(
                    id = Uuid.random().toString(),
                    profileId = event.profileId,
                    dayEpochDay = event.dayEpochDay,
                    kind = event.kind.wireName,
                    valueReal = event.valueReal,
                    unit = event.unitOverride ?: event.kind.unit,
                    source = event.source,
                    capturedAtEpochMs = event.capturedAt.toEpochMilliseconds(),
                    note = event.note,
                )
            // Append verbatim (multiple weigh-ins per day are normal data),
            // then cascade the day-projection refresh for the touched day.
            dao.insert(entity)
            projector.refresh(event.profileId, event.dayEpochDay, event.dayEpochDay)
            entity.toDomain()
        }

    override suspend fun attachAttrs(
        eventId: String,
        attrs: List<MeasurementAttr>,
    ): WloResult<Unit> =
        storageGuard("measurement.attachAttrs") {
            attrDao.upsertAll(
                attrs.map {
                    MeasurementEventAttrEntity(
                        eventId = eventId,
                        attr = it.attr,
                        valueText = it.valueText,
                        valueReal = it.valueReal,
                    )
                },
            )
        }

    override suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<MeasurementEvent>> = storageGuard("measurement.range") { dao.range(profileId, fromDay, toDay).map { it.toDomain() } }

    override suspend fun byId(eventId: String): WloResult<MeasurementEvent?> =
        storageGuard("measurement.byId") { dao.byId(eventId)?.toDomain() }

    override suspend fun delete(eventId: String): WloResult<Unit> =
        storageGuard("measurement.delete") {
            val entity = dao.byId(eventId) ?: return@storageGuard
            attrDao.deleteForEvent(eventId)
            dao.deleteById(eventId)
            projector.refresh(entity.profileId, entity.dayEpochDay, entity.dayEpochDay)
        }

    override suspend fun deleteTrendScalars(
        profileId: String,
        day: Long,
    ): WloResult<Unit> =
        storageGuard("measurement.deleteTrendScalars") {
            val trendIds =
                dao
                    .rangeOfKind(profileId, MeasurementKind.TREND.wireName, day, day)
                    .map { it.id }
            trendIds.forEach { id ->
                attrDao.deleteForEvent(id)
                dao.deleteById(id)
            }
            projector.refresh(profileId, day, day)
        }

    override fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<WloResult<List<MeasurementEvent>>> =
        dao
            .observeRange(profileId, fromDay, toDay)
            .map { events -> WloResult.ok(events.map { it.toDomain() }) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "measurement.observeRange"))) }

    override suspend fun attrsOf(eventId: String): WloResult<List<MeasurementAttr>> =
        storageGuard("measurement.attrsOf") {
            attrDao.forEvent(eventId).map { MeasurementAttr(it.eventId, it.attr, it.valueText, it.valueReal) }
        }

    override suspend fun attrsInRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<MeasurementAttr>> =
        storageGuard("measurement.attrsInRange") {
            attrDao
                .forRange(profileId, fromDay, toDay)
                .map { MeasurementAttr(it.eventId, it.attr, it.valueText, it.valueReal) }
        }
}

// --- day projection (Appendix A.3 — the only door, the only writer) --------

/**
 * The projection pipeline: aggregates event-level records (R-B8) into cached
 * day scalars + provenance rows. Internal construction — features reach it
 * only through the repositories (A.3: the day projection is the only door).
 */
public class DayProjector public constructor(
    private val db: WloDatabase,
    private val clock: ClockPort,
) {
    public suspend fun refresh(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ) {
        val events = db.measurementEvents().range(profileId, fromDay, toDay)
        val byDay = events.groupBy { it.dayEpochDay }
        // Diary entries are the R-B1 intake events (R-B8 rows); the kcal-only
        // quick-add path and F05-style burns still ride measurement_events.
        val diaryByDay =
            db
                .diaryEntries()
                .range(profileId, fromDay, toDay)
                .groupBy { it.dayEpochDay }
        val now = clock.now().toEpochMilliseconds()
        // Emptied days participate too: a day whose events are all gone (a
        // deleted weigh-in, a removed diary entry — R-B8 amendment, WLO-0035)
        // must have its cached scalars cleared, not silently kept.
        val previouslyCached =
            db
                .dayRecords()
                .range(profileId, fromDay, toDay)
                .map { it.dayEpochDay }
                .toSet()
        val days = (byDay.keys + diaryByDay.keys + previouslyCached).sorted()
        db.withWriteTransaction {
            days.forEach { day ->
                val dayEvents = byDay[day].orEmpty()
                val intakeEvents = dayEvents.filter { it.kind == MeasurementKind.INTAKE.wireName }
                val burnEvents = dayEvents.filter { it.kind == MeasurementKind.BURN.wireName }
                val trendEvent = dayEvents.lastOrNull { it.kind == MeasurementKind.TREND.wireName }
                val diaryKcal = diaryByDay[day]?.sumOf { it.computedKcal } ?: 0.0
                val hasIntake = intakeEvents.isNotEmpty() || diaryByDay[day] != null
                val intake = intakeEvents.sumOf { it.valueReal } + diaryKcal
                val burn = burnEvents.sumOf { it.valueReal }

                db.dayRecords().upsert(
                    DayRecordEntity(
                        profileId = profileId,
                        dayEpochDay = day,
                        trendWeightKg = trendEvent?.valueReal,
                        intakeKcal = if (hasIntake) intake else null,
                        burnKcal = burnEvents.takeIf { it.isNotEmpty() }?.sumOf { e -> e.valueReal },
                        computedAtEpochMs = now,
                    ),
                )
                if (hasIntake) {
                    writeProvenance(
                        profileId,
                        day,
                        "intakeKcal",
                        "sum-of-events",
                        intake,
                        dayEvents.size + (diaryByDay[day]?.size ?: 0),
                    )
                }
                if (burnEvents.isNotEmpty()) {
                    writeProvenance(profileId, day, "burnKcal", "sum-of-events", burn, dayEvents.size)
                }
                trendEvent?.let {
                    writeProvenance(
                        profileId,
                        day,
                        "trendWeightKg",
                        "trend-event",
                        it.valueReal,
                        dayEvents.size,
                    )
                }
            }
        }
    }

    private suspend fun writeProvenance(
        profileId: String,
        day: Long,
        scalar: String,
        method: String,
        value: Double,
        eventCount: Int,
    ) {
        db.provenance().upsert(
            ProvenanceEntity(
                profileId = profileId,
                dayEpochDay = day,
                scalar = scalar,
                method = method,
                formulaVersion = PROJECTION_FORMULA_VERSION,
                inputsHash = InputsHash.fnv1a64("$scalar=$value;events=$eventCount"),
                computedAtEpochMs = clock.now().toEpochMilliseconds(),
            ),
        )
    }

    public companion object {
        public const val PROJECTION_FORMULA_VERSION: String = "projection/event-sum-v1"
    }
}

public class RoomDayProjectionRepository public constructor(
    private val db: WloDatabase,
    private val projector: DayProjector,
    private val targets: TargetsRepository,
) : DayProjectionRepository {
    override fun observeDay(
        profileId: String,
        day: Long,
    ): Flow<WloResult<DayView>> =
        db
            .dayRecords()
            .observeDay(profileId, day)
            .map { record -> WloResult.ok(assemble(profileId, day, record)) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "projection.observeDay"))) }

    override fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<WloResult<List<DayView>>> =
        db
            .dayRecords()
            .observeRange(profileId, fromDay, toDay)
            .map { records -> WloResult.ok(records.map { assemble(profileId, it.dayEpochDay, it) }) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "projection.observeRange"))) }

    override suspend fun day(
        profileId: String,
        day: Long,
    ): WloResult<DayView> = storageGuard("projection.day") { assemble(profileId, day, db.dayRecords().day(profileId, day)) }

    override suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<DayView>> =
        storageGuard("projection.range") {
            db.dayRecords().range(profileId, fromDay, toDay).map { assemble(profileId, it.dayEpochDay, it) }
        }

    override suspend fun recompute(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<Unit> = storageGuard("projection.recompute") { projector.refresh(profileId, fromDay, toDay) }

    private suspend fun assemble(
        profileId: String,
        day: Long,
        record: DayRecordEntity?,
    ): DayView {
        val provenance = db.provenance().range(profileId, day, day).associateBy { it.scalar }
        val current = targets.current(profileId).getOrNull()

        fun measured(
            scalar: String,
            value: Double?,
        ): DerivedValue<Double>? {
            if (value == null) return null
            val row = provenance[scalar]
            return DerivedValue(
                value = value,
                provenance =
                    Provenance.Derived(
                        formulaVersion = row?.formulaVersion ?: DayProjector.PROJECTION_FORMULA_VERSION,
                        inputs =
                            listOf(
                                "method=${row?.method ?: "sum-of-events"}",
                                "hash=${row?.inputsHash ?: "n/a"}",
                            ),
                    ),
            )
        }

        val projection = current?.let { dayProjectionOf(it.document, it.version, day) }

        // R-B1: fold the plan's claim for this day in at read time — planned
        // vs logged stays one door, and plan edits land without a recompute.
        val planSlots =
            db
                .planSlots()
                .range(profileId, day, day)
                .map { it.toDomain() }
        val planned = PlannerEngine.plannedDayTotals(planSlots)
        val plannedProvenance =
            Provenance.Derived(
                formulaVersion = PLANNED_DAY_PROJECTION_VERSION,
                inputs = listOf("slots=${planned.slotCount}", "planId=${planSlots.firstOrNull()?.planId ?: "none"}"),
            )
        val plannedDerived =
            if (planned.slotCount > 0) {
                DayPlannedScalars(
                    kcal = DerivedValue(round1(planned.kcal), plannedProvenance),
                    proteinG = DerivedValue(round1(planned.proteinG), plannedProvenance),
                    carbG = DerivedValue(round1(planned.carbG), plannedProvenance),
                    fatG = DerivedValue(round1(planned.fatG), plannedProvenance),
                    fiberG = DerivedValue(round1(planned.fiberG), plannedProvenance),
                    slotCount = planned.slotCount,
                    openCount = planSlots.count { it.state == app.wlo.core.model.PlannedSlotState.PLANNED },
                )
            } else {
                null
            }

        return DayView(
            profileId = profileId,
            dayEpochDay = day,
            budgetKcal = projection?.budget,
            proteinG = projection?.proteinG,
            carbG = projection?.carbG,
            fatG = projection?.fatG,
            fiberG = projection?.fiberG,
            waterMl = projection?.waterMl,
            trendWeightKg = measured("trendWeightKg", record?.trendWeightKg),
            intakeKcal = measured("intakeKcal", record?.intakeKcal),
            burnKcal = measured("burnKcal", record?.burnKcal),
            plannedKcal = plannedDerived?.kcal,
            plannedProteinG = plannedDerived?.proteinG,
            plannedCarbG = plannedDerived?.carbG,
            plannedFatG = plannedDerived?.fatG,
            plannedFiberG = plannedDerived?.fiberG,
            plannedSlotCount = plannedDerived?.slotCount ?: 0,
            plannedOpenSlots = plannedDerived?.openCount ?: 0,
        )
    }
}

internal data class DayPlannedScalars(
    val kcal: DerivedValue<Double>,
    val proteinG: DerivedValue<Double>,
    val carbG: DerivedValue<Double>,
    val fatG: DerivedValue<Double>,
    val fiberG: DerivedValue<Double>,
    val slotCount: Int,
    val openCount: Int,
)

internal fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

/** R-B1 planned-side projection formula (stamped into the DerivedValue chip). */
internal const val PLANNED_DAY_PROJECTION_VERSION: String = "planner/planned-day-v1"

/** Resolved A.3 scalars for one day, each provenance-chipped to the Targets version. */
internal data class ResolvedDayProjection(
    val budget: DerivedValue<Double>?,
    val proteinG: DerivedValue<Double>?,
    val carbG: DerivedValue<Double>?,
    val fatG: DerivedValue<Double>?,
    val fiberG: DerivedValue<Double>?,
    val waterMl: DerivedValue<Double>?,
)

internal fun dayProjectionOf(
    document: TargetsDocument,
    targetsVersion: Int,
    day: Long,
): ResolvedDayProjection {
    // ISO weekday: 1 = Monday … 7 = Sunday (SettingsDocument WEEK_START_MONDAY).
    val isoDay = LocalDate.fromEpochDays(day.toInt()).dayOfWeek.isoDayNumber
    val budget = document.budgetForDay(isoDay) ?: return ResolvedDayProjection(null, null, null, null, null, null)

    fun derived(value: Double): Provenance.Derived =
        Provenance.Derived(
            formulaVersion = PROJECTION_VERSION,
            inputs = listOf("targetsVersion=$targetsVersion", "isoDay=$isoDay", "value=$value"),
        )

    fun grams(pct: Double): Double = pct / 100.0 * budget / ConstantsRegistry.KCAL_PER_G_PROTEIN

    val custom = document.macros.split as? MacroSplit.Custom
    return ResolvedDayProjection(
        budget = DerivedValue(budget, derived(budget)),
        proteinG = custom?.proteinPct?.let { DerivedValue(grams(it), derived(grams(it))) },
        carbG = custom?.carbPct?.let { DerivedValue(grams(it), derived(grams(it))) },
        fatG = custom?.fatPct?.let { DerivedValue(it / 100.0 * budget / ConstantsRegistry.KCAL_PER_G_FAT, derived(it)) },
        fiberG = DerivedValue(document.fiber.targetG, derived(document.fiber.targetG)),
        waterMl = DerivedValue(document.water.targetMl, derived(document.water.targetMl)),
    )
}

internal const val PROJECTION_VERSION: String = "targets/daily-projection-v1"

// --- targets (read door + internal two-writer store) -----------------------

public class RoomTargetsRepository public constructor(
    private val db: WloDatabase,
) : TargetsRepository {
    private val dao = db.targetsVersions()

    override suspend fun current(profileId: String): WloResult<TargetsRecord?> =
        storageGuard("targets.current") { dao.current(profileId)?.toRecord() }

    override fun observeCurrent(profileId: String): Flow<WloResult<TargetsRecord?>> =
        dao
            .observeCurrent(profileId)
            .map { entity -> WloResult.ok(entity?.toRecord()) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "targets.observeCurrent"))) }

    override suspend fun history(profileId: String): WloResult<List<TargetsRecord>> =
        storageGuard("targets.history") { dao.history(profileId).map { it.toRecord() } }
}

internal fun TargetsVersionEntity.toRecord(): TargetsRecord = TargetsDocumentIO.decode(documentJson)

internal class RoomTargetsVersionStore(
    private val db: WloDatabase,
) : TargetsVersionStore {
    private val dao = db.targetsVersions()
    private val profiles = db.profiles()

    override suspend fun current(profileId: String): TargetsRecord? = dao.current(profileId)?.toRecord()

    override suspend fun history(profileId: String): List<TargetsRecord> = dao.history(profileId).map { it.toRecord() }

    override suspend fun defaultFloorKcal(profileId: String): Double {
        val sex = Sex.fromWireName(profiles.byId(profileId)?.sex)
        return ConstantsRegistry.floorKcal(sex).toDouble()
    }

    override suspend fun write(
        profileId: String,
        writerId: TargetsWriterId,
        baseVersion: Int?,
        document: TargetsDocument,
        atEpochMs: Long,
    ): TargetsWriteOutcome =
        try {
            val outcome =
                db.withWriteTransaction {
                    val currentVersion = dao.maxVersion(profileId) ?: 0
                    if (baseVersion != null && baseVersion != currentVersion) {
                        return@withWriteTransaction TargetsWriteOutcome.Rejected(
                            TargetsWriteError.VersionConflict(baseVersion, currentVersion),
                        )
                    }
                    if (baseVersion == null && currentVersion != 0) {
                        return@withWriteTransaction TargetsWriteOutcome.Rejected(
                            TargetsWriteError.VersionConflict(1, currentVersion),
                        )
                    }
                    val nextVersion = currentVersion + 1
                    val record =
                        TargetsRecord(
                            version = nextVersion,
                            parentVersion = currentVersion.takeIf { it > 0 },
                            createdAtEpochMs = atEpochMs,
                            createdBy = writerId,
                            document = document,
                        )
                    dao.insert(
                        TargetsVersionEntity(
                            id = Uuid.random().toString(),
                            profileId = profileId,
                            version = nextVersion,
                            documentJson = TargetsDocumentIO.encode(record),
                            writtenBy = writerId.toColumnName(),
                            createdAtEpochMs = atEpochMs,
                            supersededAtEpochMs = null,
                        ),
                    )
                    if (currentVersion > 0) {
                        dao.supersede(profileId, currentVersion, atEpochMs)
                    }
                    val previous =
                        dao.history(profileId).map { it.toRecord() }.lastOrNull { it.version < nextVersion }
                    TargetsWriteOutcome.Written(
                        record = record,
                        diff = diffOf(previous, document),
                        writtenBy = writerId,
                    )
                }
            outcome
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            TargetsWriteOutcome.Rejected(TargetsWriteError.StorageFailure(t))
        }
}

internal fun TargetsWriterId.toColumnName(): String =
    when (this) {
        TargetsWriterId.STUDIO_F01 -> "F01_STUDIO"
        TargetsWriterId.APPLY_F07 -> "F07_APPLY"
    }

/** Small field-level diff for the version ribbon (A.2: "human-readable diff"). */
public fun diffOf(
    old: TargetsRecord?,
    new: TargetsDocument,
): List<String> {
    if (old == null) return listOf("plan created")
    val previous = old.document
    val changes = mutableListOf<String>()
    if (previous.goal.targetWeightKg != new.goal.targetWeightKg) {
        changes += "goal weight ${previous.goal.targetWeightKg} → ${new.goal.targetWeightKg} kg"
    }
    if (previous.goal.pacePctPerWeek != new.goal.pacePctPerWeek) {
        changes += "pace ${previous.goal.pacePctPerWeek} → ${new.goal.pacePctPerWeek} %/week"
    }
    if (new.energy.cadence == Cadence.DAILY && previous.energy.budgetKcal != new.energy.budgetKcal) {
        changes += "budget ${previous.energy.budgetKcal} → ${new.energy.budgetKcal} kcal"
    }
    if (new.energy.cadence == Cadence.WEEKLY && previous.energy.weeklyBudgetKcal != new.energy.weeklyBudgetKcal) {
        changes += "weekly budget ${previous.energy.weeklyBudgetKcal} → ${new.energy.weeklyBudgetKcal} kcal"
    }
    if (previous.energy.schedule != new.energy.schedule) changes += "schedule updated"
    if (previous.energy.floorKcal != new.energy.floorKcal) {
        changes += "floor ${previous.energy.floorKcal} → ${new.energy.floorKcal} kcal"
    }
    if (previous.macros != new.macros) changes += "macros updated"
    if (previous.fiber.targetG != new.fiber.targetG) {
        changes += "fiber ${previous.fiber.targetG} → ${new.fiber.targetG} g"
    }
    if (previous.water.targetMl != new.water.targetMl) {
        changes += "water ${previous.water.targetMl} → ${new.water.targetMl} ml"
    }
    if (changes.isEmpty()) changes += "no numeric changes (re-save)"
    return changes
}

// --- schema-only consumers (M6) — insert/read plumbing for the ledger ------

public class RoomConsentLedgerStore public constructor(
    private val db: WloDatabase,
) {
    public suspend fun append(entry: ConsentLedgerEntity): WloResult<Unit> =
        storageGuard("consent.append") { db.consentLedger().append(entry) }

    public suspend fun all(): WloResult<List<ConsentLedgerEntity>> = storageGuard("consent.all") { db.consentLedger().all() }
}
