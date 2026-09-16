package app.wlo.core.data

import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BodyMeasurementRepositoryTest {
    private val dir = Files.createTempDirectory("wlo-body-measurement-test")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val settings = SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath())
    private val profiles = RoomProfileRepository(db, settings)
    private val projector = DayProjector(db, clock)

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun bundleIsAtomicIdempotentAndMethodAware() =
        runTest {
            val profile = profile()
            val command = command(profile, "one-operation")
            val repository = RoomMeasurementRepository(db, projector)

            val first = repository.saveBodyMeasurement(command).okOrDie()
            val replay = repository.saveBodyMeasurement(command).okOrDie()

            assertEquals(first, replay)
            assertEquals(4, db.measurementEvents().count(profile))
            val chart = repository.bodyFatChart(profile, command.dayEpochDay, command.dayEpochDay).okOrDie().single()
            assertEquals("navy-tape", chart.methodId)
            assertEquals("navy-v1", chart.methodVersion)
            assertEquals(listOf("height", "hip", "neck", "waist"), chart.explanationInputs.map { it.name }.sorted())
        }

    @Test
    fun everyInjectedFailureRollsBackAndRetryCommitsOnce() =
        runTest {
            val profile = profile()
            BodyMeasurementMutationStage.entries.forEach { target ->
                val command = command(profile, "operation-${target.name}")
                val baselineEvents = db.measurementEvents().count(profile)
                val baselineAttrs = db.measurementEventAttrs().all().size
                val failing =
                    RoomMeasurementRepository(db, projector) { stage ->
                        if (stage == target) error("injected $target")
                    }

                assertIs<WloResult.Err>(failing.saveBodyMeasurement(command))
                assertEquals(baselineEvents, db.measurementEvents().count(profile), target.name)
                assertEquals(baselineAttrs, db.measurementEventAttrs().all().size, target.name)

                val retry = RoomMeasurementRepository(db, projector)
                retry.saveBodyMeasurement(command).okOrDie()
                retry.saveBodyMeasurement(command).okOrDie()
                assertEquals(baselineEvents + 4, db.measurementEvents().count(profile), target.name)
            }
        }

    @Test
    fun optionalSessionIsAtomicAndRetrySafe() =
        runTest {
            val profile = profile()
            val session =
                MeasurementSession(
                    "session",
                    profile,
                    20000,
                    clock.now(),
                    listOf(SessionReading("waist", 84.0), SessionReading("body-fat", 23.4, "scale")),
                )
            val failing =
                RoomMeasurementRepository(db, projector) { stage ->
                    if (stage == BodyMeasurementMutationStage.ATTRIBUTES_WRITTEN) error("storage unavailable")
                }
            assertIs<WloResult.Err>(failing.saveSession(session))
            assertEquals(0, db.measurementEvents().count(profile))
            val repository = RoomMeasurementRepository(db, projector)
            repository.saveSession(session).okOrDie()
            repository.saveSession(session).okOrDie()
            assertEquals(2, db.measurementEvents().count(profile))
            val body = repository.bodyFatChart(profile, 20000, 20000).okOrDie().single()
            assertEquals("scale", body.source)
            assertEquals(23.4, body.valuePercent)
            assertEquals("reported-scale", body.methodId)
            val attrs = repository.attrsInRange(profile, 20000, 20000).okOrDie()
            assertEquals(1, attrs.count { it.attr == "metric" && it.valueText == "waist" })
        }

    @Test
    fun eachCircumferenceCanBeSavedIndependently() =
        runTest {
            val profile = profile()
            val repository = RoomMeasurementRepository(db, projector)
            val metrics = listOf("waist", "hip", "chest", "left-thigh", "right-thigh", "left-arm", "right-arm")
            metrics.forEachIndexed { index, key ->
                repository
                    .saveSession(
                        MeasurementSession(
                            key,
                            profile,
                            20000,
                            clock.now(),
                            listOf(SessionReading(key, 50.0 + index)),
                        ),
                    ).okOrDie()
            }
            assertEquals(7, db.measurementEvents().count(profile))
            assertEquals(0, repository.bodyFatChart(profile, 20000, 20000).okOrDie().size)
            assertEquals(
                metrics.toSet(),
                repository
                    .attrsInRange(profile, 20000, 20000)
                    .okOrDie()
                    .filter { it.attr == "metric" }
                    .map { it.valueText }
                    .toSet(),
            )
        }

    @Test
    fun invalidSessionWritesNothing() =
        runTest {
            val profile = profile()
            val repository = RoomMeasurementRepository(db, projector)
            listOf(
                emptyList(),
                listOf(SessionReading("waist", Double.NaN)),
                listOf(SessionReading("body-fat", 100.0)),
                listOf(SessionReading("chest", -1.0)),
            ).forEach { rows ->
                assertIs<WloResult.Err>(repository.saveSession(MeasurementSession("bad", profile, 20000, clock.now(), rows)))
            }
            assertEquals(0, db.measurementEvents().count(profile))
        }

    private suspend fun profile(): String = profiles.create(NewProfile(heightCm = 170.0), clock.now()).okOrDie().id

    private fun command(
        profileId: String,
        operationId: String,
    ): BodyMeasurementCommand =
        BodyMeasurementCommand(
            operationId = operationId,
            profileId = profileId,
            dayEpochDay = DayBoundary.epochDay(clock.now(), TimeZone.currentSystemDefault()),
            capturedAt = clock.now(),
            methodId = "navy-tape",
            methodVersion = "navy-v1",
            inputs =
                listOf(
                    BodyCanonicalInput("height", 170.0),
                    BodyCanonicalInput("waist", 101.6),
                    BodyCanonicalInput("neck", 38.0),
                    BodyCanonicalInput("hip", 104.0),
                ),
            estimatePercent = 28.4,
        )

    private fun <T> WloResult<T>.okOrDie(): T =
        when (this) {
            is WloResult.Ok -> value
            is WloResult.Err -> error(error.debugMessage)
        }
}
