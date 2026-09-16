package app.wlo.core.vault

import app.wlo.core.common.ClockPort
import app.wlo.core.data.DayProjector
import app.wlo.core.data.RoomMeasurementRepository
import app.wlo.core.data.RoomWeighInRepository
import app.wlo.core.database.ProfileEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.model.MeasurementKind
import app.wlo.core.ports.HealthConnectAvailability
import app.wlo.core.ports.HealthConnectChange
import app.wlo.core.ports.HealthConnectChangePage
import app.wlo.core.ports.HealthConnectMetric
import app.wlo.core.ports.HealthConnectPermissionState
import app.wlo.core.ports.HealthConnectPort
import app.wlo.core.ports.HealthConnectRecord
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class HealthConnectSyncManagerTest {
    private lateinit var db: WloDatabase
    private lateinit var client: FakeHealthConnect
    private lateinit var manager: HealthConnectSyncManager
    private var now = 2_000_000L

    @Before
    fun setUp() {
        db = jvmDatabaseBuilder(Files.createTempFile("hc-sync", ".db").toString()).build()
        client = FakeHealthConnect()
        manager = createManager()
    }

    private fun createManager(): HealthConnectSyncManager {
        val clock = ClockPort { Instant.fromEpochMilliseconds(now) }
        val projector = DayProjector(db, clock)
        val measurements = RoomMeasurementRepository(db, projector)
        return HealthConnectSyncManager(client, db, RoomWeighInRepository(db, measurements, projector)) { now++ }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun initialReplayUpdateAndDelete_areIdentityDrivenAndDurable() =
        runTest {
            profile()
            val initial = record("hc-1", version = 1, value = 82.4, zoneOffsetSeconds = 14 * 3_600)
            client.history[HealthConnectMetric.WEIGHT] = listOf(initial)

            val first = manager.syncNow(PROFILE)
            assertEquals(1, first.inserted)
            val firstIdentity = db.healthConnect().record("hc-1")!!
            assertEquals(82.4, db.measurementEvents().byId(firstIdentity.measurementEventId)!!.valueReal)

            client.pages["weight-2"] =
                HealthConnectChangePage(
                    listOf(HealthConnectChange.Upsert(initial)),
                    "weight-3",
                    hasMore = false,
                    tokenExpired = false,
                )
            manager = createManager() // process restart: cursor exists only in Room, not manager memory
            val replay = manager.syncNow(PROFILE)
            assertEquals(1, replay.skipped)
            assertEquals(1, db.healthConnect().records(PROFILE, "weight").size)

            client.pages["weight-3"] =
                HealthConnectChangePage(
                    listOf(HealthConnectChange.Upsert(initial.copy(clientRecordVersion = 2, canonicalValue = 81.7))),
                    "weight-4",
                    hasMore = false,
                    tokenExpired = false,
                )
            val updated = manager.syncNow(PROFILE)
            assertEquals(1, updated.updated)
            val secondIdentity = db.healthConnect().record("hc-1")!!
            assertNotEquals(firstIdentity.measurementEventId, secondIdentity.measurementEventId)
            assertNull(db.measurementEvents().byId(firstIdentity.measurementEventId))
            assertEquals(81.7, db.measurementEvents().byId(secondIdentity.measurementEventId)!!.valueReal)

            client.pages["weight-4"] =
                HealthConnectChangePage(
                    listOf(HealthConnectChange.Delete("hc-1")),
                    "weight-5",
                    hasMore = false,
                    tokenExpired = false,
                )
            val deleted = manager.syncNow(PROFILE)
            assertEquals(1, deleted.deleted)
            assertNull(db.healthConnect().record("hc-1"))
            assertNull(db.measurementEvents().byId(secondIdentity.measurementEventId))
        }

    @Test
    fun sameInstantDifferentIdsAndTimezoneTravel_preserveDistinctEvents() =
        runTest {
            profile()
            val east = record("east", version = 1, value = 80.0, zoneOffsetSeconds = 14 * 3_600)
            val west = record("west", version = 1, value = 80.0, zoneOffsetSeconds = -10 * 3_600)
            client.history[HealthConnectMetric.WEIGHT] = listOf(east, west)

            val result = manager.syncNow(PROFILE)

            assertEquals(2, result.inserted)
            val events = db.measurementEvents().rangeOfKind(PROFILE, MeasurementKind.WEIGHT.wireName, Long.MIN_VALUE, Long.MAX_VALUE)
            assertEquals(2, events.size)
            assertNotEquals(events[0].dayEpochDay, events[1].dayEpochDay)
        }

    @Test
    fun revokedPermission_isVisibleAndDoesNotAdvanceCursor() =
        runTest {
            profile()
            client.permissions = HealthConnectPermissionState(false, false)
            val result = manager.syncNow(PROFILE)
            assertEquals("permission-revoked", result.outcome)
            assertNull(db.healthConnect().syncState(PROFILE, HealthConnectMetric.WEIGHT.wireName))
            assertEquals("permission-revoked", db.healthConnect().latestLog(PROFILE)?.outcome)
        }

    @Test
    fun bodyFatAndExpiredCursor_fullReconcileWithoutTouchingManualEvent() =
        runTest {
            profile()
            client.permissions = HealthConnectPermissionState(true, true)
            val weight = record("weight-old", 1, 83.0, 0)
            val fat =
                record("fat-1", 1, 24.5, 0).copy(metric = HealthConnectMetric.BODY_FAT)
            client.history[HealthConnectMetric.WEIGHT] = listOf(weight)
            client.history[HealthConnectMetric.BODY_FAT] = listOf(fat)
            manager.syncNow(PROFILE)
            val manualId = "manual-weight"
            db.measurementEvents().insert(
                app.wlo.core.database.MeasurementEventEntity(
                    manualId,
                    PROFILE,
                    20_700,
                    "weight",
                    84.0,
                    "kg",
                    "manual",
                    1,
                ),
            )

            client.pages["weight-2"] = HealthConnectChangePage(emptyList(), "ignored", false, true)
            client.history[HealthConnectMetric.WEIGHT] = listOf(record("weight-new", 1, 82.8, 0))
            val result = manager.syncNow(PROFILE)

            assertEquals(1, result.deleted)
            assertEquals(1, result.inserted)
            assertNull(db.healthConnect().record("weight-old"))
            assertEquals(24.5, db.measurementEvents().byId(db.healthConnect().record("fat-1")!!.measurementEventId)!!.valueReal)
            assertEquals(84.0, db.measurementEvents().byId(manualId)!!.valueReal)
        }

    @Test
    fun providerFailure_isRetryableAndLeavesCursorUnchanged() =
        runTest {
            profile()
            client.failRead = true
            val result = manager.syncNow(PROFILE)
            assertEquals("retry", result.outcome)
            assertEquals(true, result.retryable)
            assertNull(db.healthConnect().syncState(PROFILE, "weight"))
        }

    private suspend fun profile() {
        db.profiles().upsert(ProfileEntity(PROFILE, null, null, null, null, "sedentary", "metric", 1))
    }

    private fun record(
        id: String,
        version: Long,
        value: Double,
        zoneOffsetSeconds: Int,
    ): HealthConnectRecord =
        HealthConnectRecord(
            recordId = id,
            dataOriginPackage = "com.example.scale",
            clientRecordId = "client-$id",
            clientRecordVersion = version,
            recordingMethod = 1,
            lastModifiedAtEpochMs = version,
            capturedAtEpochMs = 1_788_523_200_000,
            zoneOffsetSeconds = zoneOffsetSeconds,
            metric = HealthConnectMetric.WEIGHT,
            canonicalValue = value,
        )

    private class FakeHealthConnect : HealthConnectPort {
        var permissions = HealthConnectPermissionState(true, false)
        val history = mutableMapOf<HealthConnectMetric, List<HealthConnectRecord>>()
        val pages = mutableMapOf<String, HealthConnectChangePage>()
        var failRead = false

        override suspend fun availability() = HealthConnectAvailability.AVAILABLE

        override suspend fun permissionState() = permissions

        override suspend fun readAll(metric: HealthConnectMetric): List<HealthConnectRecord> {
            if (failRead) error("provider unavailable")
            return history[metric].orEmpty()
        }

        override suspend fun changesToken(metric: HealthConnectMetric) = "${metric.wireName}-1"

        override suspend fun changes(
            metric: HealthConnectMetric,
            token: String,
        ): HealthConnectChangePage = pages[token] ?: HealthConnectChangePage(emptyList(), next(token), false, false)

        private fun next(token: String): String = token.substringBeforeLast('-') + "-" + (token.substringAfterLast('-').toInt() + 1)
    }

    private companion object {
        const val PROFILE = "profile-1"
    }
}
