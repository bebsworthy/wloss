package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.model.MeasurementAttr
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.model.Profile
import app.wlo.core.model.Provenance
import app.wlo.core.model.UnitSystem
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repository tests on the JVM Room driver (T-J): the spine doors behave —
 * profiles, append-only events (R-B8), the day projection (A.3), provenance.
 */
class SpineRepositoriesTest {
    private val dir = Files.createTempDirectory("wlo-spine-test")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val settings = SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath())

    private val profiles = RoomProfileRepository(db, settings)
    private val projector = DayProjector(db, clock)
    private val measurements = RoomMeasurementRepository(db, projector)
    private val targets = RoomTargetsRepository(db)
    private val projection = RoomDayProjectionRepository(db, projector, targets)

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun aProfile(unit: UnitSystem = UnitSystem.METRIC): String =
        profiles
            .create(
                NewProfile(
                    birthYear = 1994,
                    heightCm = 165.0,
                    startWeightKg = 84.2,
                    unitPreference = unit,
                ),
                clock.now(),
            ).okOrDie()
            .id

    private fun <T> WloResult<T>.okOrDie(): T =
        when (this) {
            is WloResult.Ok -> value
            is WloResult.Err -> error("unexpected storage error: ${error.debugMessage} (cause: ${error.cause})")
        }

    @Test
    fun profileCreateReadsAndArchives() =
        runTest {
            val id = aProfile()
            val active = profiles.active()
            assertIs<WloResult.Ok<Profile?>>(active)
            assertEquals(id, active.value?.id)
            assertEquals(UnitSystem.METRIC, active.value?.unitPreference)

            profiles.archive(id, clock.now())
            assertNull(profiles.active().getOrNull())
            // Archive-don't-delete: the row remains queryable.
            assertEquals(id, profiles.byId(id).getOrNull()?.id)
        }

    @Test
    fun unitPreferenceWritesRowAndSettingsStore() =
        runTest {
            val id = aProfile()
            profiles.setUnitPreference(id, UnitSystem.IMPERIAL)
            assertEquals(UnitSystem.IMPERIAL, profiles.byId(id).getOrNull()?.unitPreference)
            assertEquals(app.wlo.core.common.MassUnit.POUND, settings.massUnit.first())
        }

    @Test
    fun measurementEventsAreAppendOnlyAndKeptVerbatim() =
        runTest {
            val id = aProfile()
            // R-B8: two weigh-ins on one day are two events, kept verbatim.
            measurements.append(
                NewMeasurement(id, dayEpochDay = 20_708L, MeasurementKind.WEIGHT, 84.2, MeasurementSource.SCALE, clock.now()),
            )
            measurements.append(
                NewMeasurement(id, dayEpochDay = 20_708L, MeasurementKind.WEIGHT, 83.9, MeasurementSource.MANUAL, clock.now()),
            )
            measurements.append(
                NewMeasurement(id, dayEpochDay = 20_708L, MeasurementKind.INTAKE, 1_900.0, MeasurementSource.MANUAL, clock.now()),
            )

            val events = measurements.range(id, 20_708L, 20_708L).getOrNull()!!
            assertEquals(3, events.size, "multiple weigh-ins per day are normal data")
            assertEquals(listOf(84.2, 83.9), events.filter { it.kind == MeasurementKind.WEIGHT }.map { it.valueReal })
            assertEquals("kg", events.first { it.kind == MeasurementKind.WEIGHT }.unit)
            assertEquals("kcal", events.first { it.kind == MeasurementKind.INTAKE }.unit)
            assertTrue(
                measurements
                    .observeRange(id, 20_708L, 20_708L)
                    .first()
                    .getOrNull()!!
                    .size == 3,
            )
        }

    @Test
    fun eavSidecarStoresCustomAttributes() =
        runTest {
            val id = aProfile()
            val event =
                (
                    measurements.append(
                        NewMeasurement(id, 20_708L, MeasurementKind.WEIGHT, 84.2, MeasurementSource.SCALE, clock.now()),
                    ) as WloResult.Ok
                ).value
            measurements.attachAttrs(
                event.id,
                listOf(
                    MeasurementAttr(event.id, "body-fat-pct", valueReal = 17.4),
                    MeasurementAttr(event.id, "scale-model", valueText = "ACME-100"),
                ),
            )
            val attrs = measurements.attrsOf(event.id).getOrNull()!!
            assertEquals(2, attrs.size)
            assertEquals(17.4, attrs.first { it.attr == "body-fat-pct" }.valueReal)
            assertEquals("ACME-100", attrs.first { it.attr == "scale-model" }.valueText)
        }

    @Test
    fun dayProjectionAggregatesEventsAndChipsProvenance() =
        runTest {
            val id = aProfile()
            measurements.append(NewMeasurement(id, 20_708L, MeasurementKind.INTAKE, 1_500.0, MeasurementSource.MANUAL, clock.now()))
            measurements.append(NewMeasurement(id, 20_708L, MeasurementKind.INTAKE, 400.0, MeasurementSource.MANUAL, clock.now()))
            measurements.append(NewMeasurement(id, 20_708L, MeasurementKind.BURN, 300.0, MeasurementSource.MANUAL, clock.now()))
            measurements.append(NewMeasurement(id, 20_708L, MeasurementKind.TREND, 84.0, MeasurementSource.ENGINE, clock.now()))

            val view = projection.day(id, 20_708L).getOrNull()!!
            assertEquals(1_900.0, view.intakeKcal?.value, "sum-of-events for intake")
            assertEquals(300.0, view.burnKcal?.value)
            assertEquals(84.0, view.trendWeightKg?.value, "last trend event of the day wins (F06 owns smoothing)")
            assertIs<Provenance.Derived>(view.intakeKcal?.provenance)
            // No targets yet: the targets half of the projection is absent.
            assertNull(view.budgetKcal)
        }

    @Test
    fun dayProjectionResolvesTargetsDailyAndWeekly() =
        runTest {
            val id = aProfile()
            targetsWriterSetup()

            val monday = 20_703L // 2026-09-07, a Monday
            val daily = projection.day(id, monday).getOrNull()!!
            assertEquals(1_900.0, daily.budgetKcal?.value, "daily cadence → flat budget")
            assertEquals(142.5, daily.proteinG?.value, "30% of 1900 at Atwater 4 kcal/g")
            assertEquals(25.0, daily.fiberG?.value)
            assertEquals(2_000.0, daily.waterMl?.value)

            // Weekly cadence resolves schedule[isoDay − 1] (A.3: consumers
            // never parse the schedule themselves — the projection does).
            // Epoch-day weekdays: 20_703 = Mon 2026-09-07 … 20_709 = Sun 09-13.
            recomputeWeekly()
            val friday = projection.day(id, 20_707L).getOrNull()!!
            assertEquals(1_800.0, friday.budgetKcal?.value, "Friday bar of the 5×1800/2×2400 schedule")
            val sunday = projection.day(id, 20_709L).getOrNull()!!
            assertEquals(2_400.0, sunday.budgetKcal?.value, "weekend bar")
        }

    private suspend fun targetsWriterSetup() {
        // A daily-cadence plan v1, then a weekly-cadence revision v2 (Studio).
        val writers = TargetsWriters(db, clock)
        val studio = writers.studio() as F01StudioWriter
        studio.writeFirst(
            profiles.active().getOrNull()!!.id,
            validDocument(),
        )
    }

    private suspend fun recomputeWeekly() {
        val id = profiles.active().getOrNull()!!.id
        val studio = (TargetsWriters(db, clock).studio()) as F01StudioWriter
        val current = targets.current(id).getOrNull()!!
        studio.writeRevision(
            id,
            baseVersion = current.version,
            document =
                validDocument().copy(
                    energy =
                        validDocument().energy.copy(
                            cadence = app.wlo.core.documents.Cadence.WEEKLY,
                            budgetKcal = null,
                            weeklyBudgetKcal = 5 * 1_800.0 + 2 * 2_400.0,
                            schedule = List(5) { 1_800.0 } + List(2) { 2_400.0 },
                        ),
                ),
        )
        projection.recompute(id, 20_700L, 20_720L).getOrNull()
    }

    private fun validDocument() =
        app.wlo.core.documents.TargetsDocument(
            goal =
                app.wlo.core.documents
                    .Goal(targetWeightKg = 78.0, pacePctPerWeek = 0.5),
            energy =
                app.wlo.core.documents.Energy(
                    cadence = app.wlo.core.documents.Cadence.DAILY,
                    budgetKcal = 1_900.0,
                    floorKcal = 1_500.0,
                ),
            macros =
                app.wlo.core.documents.Macros(
                    split =
                        app.wlo.core.documents.MacroSplit.Custom(
                            proteinPct = 30.0,
                            carbPct = 40.0,
                            fatPct = 30.0,
                        ),
                ),
        )
}
