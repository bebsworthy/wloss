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
import kotlin.test.assertTrue

/**
 * The logbook's edit door (WLO-0056): correcting an entry REPLACES it —
 * append the corrected reading, carry the original's sidecar attrs, mark the
 * replacement as edited, retire the original. The original's own reveal:
 * even a σ-breaking correction stores the event (the outlier flag rides on
 * it) and the day's TREND scalar recomputes.
 */
class WeighInEditReplaceTest {
    private val dir = Files.createTempDirectory("wlo-weighin-edit-test")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val settings = SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath())

    private val profiles = RoomProfileRepository(db, settings)
    private val projector = DayProjector(db, clock)
    private val measurements = RoomMeasurementRepository(db, projector)
    private val targets = RoomTargetsRepository(db)
    private val projection = RoomDayProjectionRepository(db, projector, targets)
    private val weighIns = RoomWeighInRepository(db, measurements, projector)

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

    @Test
    fun replaceAppendsCorrectedMarksEditedAndRetiresTheOriginal() {
        runTest {
            val profile = aProfile()
            val day = today()
            // A season of readings so the trend and σ are established, then
            // the entry the user will correct.
            repeat(30) { index -> weighIn(profile, day - 30 + index, 77.0) }
            val original = weighIn(profile, day, 77.0)

            // The repository owns the whole replacement transaction: corrected
            // event + flags + retirement + derived suffix.
            clock.advanceBy(60_000)
            val corrected =
                weighIns
                    .replaceWeighIn(
                        eventId = original.event.id,
                        dayEpochDay = day,
                        weightKg = 80.4,
                        capturedAt = clock.now(),
                        editedDescription = "was 77.0",
                    ).okOrDie()
            val replacement = corrected.replacement.event

            val dayEvents =
                measurements
                    .range(profile, day, day)
                    .okOrDie()
                    .filter { it.kind == MeasurementKind.WEIGHT }
            assertEquals(listOf(80.4), dayEvents.map { it.valueReal })
            val attrs = measurements.attrsOf(replacement.id).okOrDie()
            assertTrue(attrs.any { it.attr == WeighInAttribute.EDITED.wireName }, "the replacement carries the edited mark")
            assertTrue(attrs.none { it.eventId == original.event.id }, "the original is gone with its sidecar")
        }
    }

    @Test
    fun correctionOperationTokenIsDurableAndIdempotent() =
        runTest {
            val profile = aProfile()
            val day = today()
            val original = weighIn(profile, day, 120.0)
            val command =
                WeighInWriteCommand.Correction(
                    operationId = "correct-once",
                    profileId = profile,
                    originalEventId = original.event.id,
                    dayEpochDay = day,
                    weightKg = 82.0,
                    capturedAt = clock.now(),
                    editedDescription = "Corrected manual reading",
                )

            val first = weighIns.commitWeighIn(command).okOrDie()
            val replay = weighIns.commitWeighIn(command).okOrDie()

            assertEquals(first.event.id, replay.event.id)
            val raw = measurements.rangeOfKind(profile, MeasurementKind.WEIGHT, day, day).okOrDie()
            assertEquals(listOf(82.0), raw.map { it.valueReal })
            assertTrue(
                measurements.attrsOf(first.event.id).okOrDie().any {
                    it.attr == WeighInAttribute.OPERATION_ID.wireName && it.valueText == "correct-once"
                },
            )
        }
}
