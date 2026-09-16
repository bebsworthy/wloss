package app.wlo.feature.f06.weight.state

import app.wlo.core.data.DeletedWeighIn
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.model.MeasurementAttr
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogbookDeletionRecoveryStoreTest {
    @Test
    fun completeReceiptSurvivesRecreationAndCanBeRetired() =
        runTest {
            val file =
                Files
                    .createTempDirectory("wlo-logbook-recovery")
                    .resolve("documents.preferences_pb")
                    .toString()
                    .toPath()
            val documents = JsonDocumentStore.create(file)
            val store = LogbookDeletionRecoveryStore(documents)
            val event =
                MeasurementEvent(
                    id = "event-1",
                    profileId = "profile-1",
                    dayEpochDay = 20_000,
                    kind = MeasurementKind.WEIGHT,
                    valueReal = 82.4,
                    unit = "kg",
                    source = "manual",
                    capturedAt = Instant.parse("2026-09-16T07:00:00Z"),
                )
            val expected =
                PendingLogbookDeletion(
                    operationId = "delete-1",
                    snapshot = DeletedWeighIn(event, listOf(MeasurementAttr(event.id, "edited", "corrected"))),
                    remainingForegroundMillis = 9_000,
                )

            store.write(expected)
            assertEquals(expected, store.read(event.profileId))
            store.clear(event.profileId)
            assertNull(store.read(event.profileId))
        }
}
