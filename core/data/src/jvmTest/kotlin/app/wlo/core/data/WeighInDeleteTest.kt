package app.wlo.core.data

import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The delete door (R-B8 amendment, WLO-0035): a user-initiated hard delete
 * drops the event everywhere BY CONSTRUCTION — day views, lowest-of-day,
 * daily scalars, the trend series — and the day's persisted TREND scalar
 * recomputes (or drops when the day empties) so no stale scalar survives.
 */
class WeighInDeleteTest {
    private val dir = Files.createTempDirectory("wlo-weighin-delete-test")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val settings = SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath())

    private val profiles = RoomProfileRepository(db, settings)
    private val projector = DayProjector(db, clock)
    private val measurements = RoomMeasurementRepository(db, projector)
    private val targets = RoomTargetsRepository(db)
    private val projection = RoomDayProjectionRepository(db, projector, targets)
    private val weighIns = RoomWeighInRepository(measurements)

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun <T> WloResult<T>.okOrDie(): T =
        when (this) {
            is WloResult.Ok -> value
            is WloResult.Err -> error("unexpected error: ${error.debugMessage}")
        }

    private suspend fun aProfile(): String =
        profiles
            .create(
                NewProfile(birthYear = 1990, heightCm = 178.0, startWeightKg = 95.0),
                clock.now(),
            ).okOrDie()
            .id

    private suspend fun weighIn(
        profileId: String,
        day: Long,
        kg: Double,
    ): WeighInOutcome {
        clock.advanceBy(60_000)
        return weighIns
            .appendWeighIn(
                profileId = profileId,
                dayEpochDay = day,
                weightKg = kg,
                capturedAt = clock.now(),
                source = MeasurementSource.MANUAL,
            ).okOrDie()
    }

    private fun today(): Long = DayBoundary.epochDay(clock.now(), TimeZone.currentSystemDefault())

    private suspend fun dayTrendScalarIds(
        profileId: String,
        day: Long,
    ): List<String> =
        measurements
            .range(profileId, day, day)
            .okOrDie()
            .filter { it.kind == MeasurementKind.TREND }
            .map { it.id }

    private suspend fun dayViewTrendKg(
        profileId: String,
        day: Long,
    ): Double? =
        projection
            .day(profileId, day)
            .okOrDie()
            .trendWeightKg
            ?.value

    // --- the typo case: the flagged event used to win lowest-of-day; not anymore ---

    @Test
    fun deleteRemovesEventEverywhereAndRecomputesDayTrend() =
        runTest {
            val profileId = aProfile()
            val day = today()

            // Footing for the trend, then the typo + the real reading.
            for (back in 7L downTo 1L) weighIn(profileId, day - back, 95.0 + (7 - back) * 0.1)
            val typo = weighIn(profileId, day, 5.9)
            val real = weighIn(profileId, day, 95.2)

            // Before: the typo poisons the day — lowest-of-day picks it.
            assertEquals(5.9, weighIns.lowestOfDay(profileId, day).okOrDie()?.valueReal)

            val deleted = weighIns.deleteWeighIn(typo.event.id, clock.now()).okOrDie()
            assertEquals(5.9, deleted.event.valueReal)
            assertEquals(1, deleted.attrs.size, "the outlier flag rides the snapshot for undo")

            // After: gone from the day view, the scalar view, and the trend input.
            assertTrue(weighIns.dayWeighIns(profileId, day).okOrDie().none { it.id == typo.event.id })
            assertEquals(95.2, weighIns.lowestOfDay(profileId, day).okOrDie()?.valueReal)
            assertTrue(
                weighIns.dailyScalars(profileId, day - 14, day).okOrDie().none { it.epochDay == day && it.weightKg == 5.9 },
            )

            // The persisted TREND scalar recomputed: the day projection now reads
            // the trend WITHOUT the typo — the canonical door's answer, not the
            // stale one computed while the typo still stood.
            val canonical = weighIns.currentTrend(profileId, day).okOrDie()
            assertEquals(canonical.current?.value, dayViewTrendKg(profileId, day))
            assertNull(weighIns.dayWeighIns(profileId, day).okOrDie().firstOrNull { it.valueReal == 5.9 })
        }

    @Test
    fun deletingTheLastWeighInOfADayDropsItsTrendScalar() =
        runTest {
            val profileId = aProfile()
            val day = today()
            for (back in 7L downTo 1L) weighIn(profileId, day - back, 95.0)
            val only = weighIn(profileId, day, 94.8)
            assertEquals(1, dayTrendScalarIds(profileId, day).size, "the append persists a day TREND scalar")

            weighIns.deleteWeighIn(only.event.id, clock.now()).okOrDie()

            // The day emptied: no trend scalar may outlive it.
            assertTrue(dayTrendScalarIds(profileId, day).isEmpty(), "stale TREND scalars go with the day's last weigh-in")
            assertNull(dayViewTrendKg(profileId, day))
            assertTrue(weighIns.dailyScalars(profileId, day - 14, day).okOrDie().none { it.epochDay == day })
        }

    @Test
    fun sidecarRowsGoWithTheEvent() =
        runTest {
            val profileId = aProfile()
            val day = today()
            for (back in 3L downTo 1L) weighIn(profileId, day - back, 95.0)
            val flagged = weighIn(profileId, day, 5.9) // ±3σ off → flagged
            weighIns.deleteWeighIn(flagged.event.id, clock.now()).okOrDie()
            assertTrue(measurements.attrsOf(flagged.event.id).okOrDie().isEmpty())
        }

    @Test
    fun deleteRefusesNonWeighInEvents() =
        runTest {
            val profileId = aProfile()
            val day = today()
            weighIn(profileId, day, 95.0)
            val trendEvent = dayTrendScalarIds(profileId, day).single()
            assertIs<WloResult.Err>(weighIns.deleteWeighIn(trendEvent, clock.now()))
        }
}
