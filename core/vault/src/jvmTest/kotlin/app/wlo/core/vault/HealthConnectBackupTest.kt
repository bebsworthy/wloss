package app.wlo.core.vault

import kotlinx.serialization.json.JsonObject
import org.junit.Test
import kotlin.test.assertEquals

class HealthConnectBackupTest {
    @Test
    fun sourceIdentityCursorAndReceipt_roundTripThroughBackupDocument() {
        val section =
            HealthConnectSection(
                records =
                    listOf(
                        HealthConnectRecordRow(
                            recordId = "hc-1",
                            profileId = "profile-1",
                            measurementEventId = "event-1",
                            dataOriginPackage = "com.example.scale",
                            clientRecordId = "client-1",
                            clientRecordVersion = 7,
                            recordingMethod = 1,
                            lastModifiedAtEpochMs = 10,
                            capturedAtEpochMs = 9,
                            zoneOffsetSeconds = 3_600,
                            metric = "weight",
                            canonicalValue = 81.2,
                        ),
                    ),
                syncStates = listOf(HealthConnectSyncStateRow("profile-1", "weight", "opaque-token", 11)),
                logs = listOf(HealthConnectLogRow("log-1", "profile-1", 12, "success", 1, 0, 0, 0, 0, false)),
            )

        val decoded = decodePayloadSections(JsonObject(BackupCodec.sectionsOf(BackupPayload(healthConnect = section))))

        assertEquals(section, decoded.healthConnect)
    }
}
