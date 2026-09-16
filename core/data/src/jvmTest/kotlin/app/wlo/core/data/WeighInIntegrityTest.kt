package app.wlo.core.data

import app.wlo.core.common.AppError
import app.wlo.core.common.WloResult
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WeighInIntegrityTest {
    private val dir = Files.createTempDirectory("wlo-weighin-integrity-test")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val settings = SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath())
    private val profiles = RoomProfileRepository(db, settings)
    private val projector = DayProjector(db, clock)
    private val measurements = RoomMeasurementRepository(db, projector)
    private val targets = RoomTargetsRepository(db)
    private val projections = RoomDayProjectionRepository(db, projector, targets)
    private var failAt: WeighInMutationStage? = null
    private val weighIns =
        RoomWeighInRepository(db, measurements, projector) { stage ->
            if (stage == failAt) error("injected failure at $stage")
        }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `invalid canonical kg never reaches storage`() =
        runTest {
            val profile = profile()
            val invalid = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0, 29.9, 300.1)

            invalid.forEach { kg ->
                val result = append(profile, 100, kg)
                assertIs<AppError.InvalidInput>(assertIs<WloResult.Err>(result).error, "$kg")
            }

            assertTrue(weights(profile, 0, 200).isEmpty())
        }

    @Test
    fun `append failure rolls raw sidecar trend and projection back together`() =
        runTest {
            val profile = profile()
            repeat(7) { append(profile, 100L + it, 95.0).okOrDie() }
            val beforeEvents = measurements.range(profile, 0, 200).okOrDie()
            val beforeProjection =
                projections
                    .day(profile, 106)
                    .okOrDie()
                    .trendWeightKg
                    ?.value
            failAt = WeighInMutationStage.ATTRIBUTES_WRITTEN

            assertIs<WloResult.Err>(append(profile, 107, 59.0))

            assertEquals(beforeEvents, measurements.range(profile, 0, 200).okOrDie())
            assertEquals(
                beforeProjection,
                projections
                    .day(profile, 106)
                    .okOrDie()
                    .trendWeightKg
                    ?.value,
            )
            assertTrue(measurements.attrsInRange(profile, 107, 107).okOrDie().isEmpty())
        }

    @Test
    fun `delete failure restores raw sidecar trend and projection together`() =
        runTest {
            val profile = profile()
            repeat(7) { append(profile, 100L + it, 95.0 - it).okOrDie() }
            val target = weights(profile, 103, 103).single()
            val beforeEvents = measurements.range(profile, 0, 200).okOrDie()
            val beforeAttrs = measurements.attrsInRange(profile, 0, 200).okOrDie()
            val beforeProjection = projections.day(profile, 106).okOrDie()
            failAt = WeighInMutationStage.ORIGINAL_DELETED

            assertIs<WloResult.Err>(weighIns.deleteWeighIn(target.id, clock.now()))

            assertEquals(beforeEvents, measurements.range(profile, 0, 200).okOrDie())
            assertEquals(beforeAttrs, measurements.attrsInRange(profile, 0, 200).okOrDie())
            assertEquals(beforeProjection, projections.day(profile, 106).okOrDie())
        }

    @Test
    fun `replace failure cannot delete original or leave a duplicate`() =
        runTest {
            val profile = profile()
            val original = append(profile, 100, 95.0).okOrDie().event
            val before = measurements.range(profile, 100, 100).okOrDie()
            failAt = WeighInMutationStage.ORIGINAL_DELETED

            val result =
                weighIns.replaceWeighIn(
                    eventId = original.id,
                    dayEpochDay = 100,
                    weightKg = 94.0,
                    capturedAt = clock.now(),
                    editedDescription = "was 95.0",
                )

            assertIs<WloResult.Err>(result)
            assertEquals(before, measurements.range(profile, 100, 100).okOrDie())
            assertEquals(original, measurements.byId(original.id).okOrDie())
        }

    @Test
    fun `backdated append and delete rebuild every later trend from raw weights`() =
        runTest {
            val profile = profile()
            for (day in 100L..106L) append(profile, day, 100.0 - (day - 100) * 0.2).okOrDie()
            val beforeLast = persistedTrend(profile, 106)

            val backdated = append(profile, 100, 80.0).okOrDie()
            assertNotEquals(beforeLast, persistedTrend(profile, 106), "the later EWMA point must move")
            assertSuffixMatchesFreshCalculation(profile, 100L..106L)

            weighIns.deleteWeighIn(backdated.event.id, clock.now()).okOrDie()
            assertSuffixMatchesFreshCalculation(profile, 100L..106L)
            assertEquals(beforeLast, persistedTrend(profile, 106))
        }

    @Test
    fun `replacement across days repairs suffix and preserves exactly one corrected event`() =
        runTest {
            val profile = profile()
            for (day in 100L..106L) append(profile, day, 95.0).okOrDie()
            val original = weights(profile, 102, 102).single()

            val replaced =
                weighIns
                    .replaceWeighIn(
                        eventId = original.id,
                        dayEpochDay = 99,
                        weightKg = 90.0,
                        capturedAt = clock.now(),
                        editedDescription = "was 95.0",
                    ).okOrDie()

            assertEquals(null, measurements.byId(original.id).okOrDie())
            assertEquals(listOf(replaced.replacement.event), weights(profile, 99, 99))
            assertTrue(weights(profile, 102, 102).isEmpty())
            assertSuffixMatchesFreshCalculation(profile, 99L..106L)
        }

    private suspend fun assertSuffixMatchesFreshCalculation(
        profile: String,
        days: LongRange,
    ) {
        days.forEach { day ->
            val rawOnDay = weights(profile, day, day)
            val expected =
                if (rawOnDay.isEmpty()) {
                    null
                } else {
                    weighIns
                        .trend(profile, day - RoomWeighInRepository.TREND_WINDOW_DAYS + 1, day)
                        .okOrDie()
                        .points
                        .last()
                        .trendKg
                        .value
                }
            assertEquals(expected, persistedTrend(profile, day), "persisted trend on day $day")
            val projected =
                projections
                    .day(profile, day)
                    .okOrDie()
                    .trendWeightKg
                    ?.value
            assertEquals(expected, projected, "projection on day $day")
        }
    }

    private suspend fun persistedTrend(
        profile: String,
        day: Long,
    ): Double? =
        measurements
            .rangeOfKind(profile, MeasurementKind.TREND, day, day)
            .okOrDie()
            .singleOrNull()
            ?.valueReal

    private suspend fun weights(
        profile: String,
        fromDay: Long,
        toDay: Long,
    ) = measurements.rangeOfKind(profile, MeasurementKind.WEIGHT, fromDay, toDay).okOrDie()

    private suspend fun append(
        profile: String,
        day: Long,
        kg: Double,
    ): WloResult<WeighInOutcome> =
        weighIns.appendWeighIn(
            profileId = profile,
            dayEpochDay = day,
            weightKg = kg,
            capturedAt = clock.now(),
            source = MeasurementSource.MANUAL,
        )

    private suspend fun profile(): String =
        profiles
            .create(
                NewProfile(birthYear = 1990, heightCm = 178.0, startWeightKg = 95.0),
                clock.now(),
            ).okOrDie()
            .id

    private fun <T> WloResult<T>.okOrDie(): T =
        when (this) {
            is WloResult.Ok -> value
            is WloResult.Err -> error("unexpected error: ${error.debugMessage}")
        }
}
