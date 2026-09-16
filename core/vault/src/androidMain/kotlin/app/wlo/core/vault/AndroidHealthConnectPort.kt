package app.wlo.core.vault

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.wlo.core.ports.HealthConnectAvailability
import app.wlo.core.ports.HealthConnectChange
import app.wlo.core.ports.HealthConnectChangePage
import app.wlo.core.ports.HealthConnectMetric
import app.wlo.core.ports.HealthConnectPermissionState
import app.wlo.core.ports.HealthConnectPort
import app.wlo.core.ports.HealthConnectRecord
import java.time.Instant
import kotlin.reflect.KClass

/** Thin AndroidX adapter; all synchronization policy remains in common code. */
public class AndroidHealthConnectPort(
    context: Context,
) : HealthConnectPort {
    private val appContext = context.applicationContext
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(appContext) }

    override suspend fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(appContext)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }

    override suspend fun permissionState(): HealthConnectPermissionState {
        val granted = client.permissionController.getGrantedPermissions()
        return HealthConnectPermissionState(WEIGHT_PERMISSION in granted, BODY_FAT_PERMISSION in granted)
    }

    override suspend fun readAll(metric: HealthConnectMetric): List<HealthConnectRecord> =
        when (metric) {
            HealthConnectMetric.WEIGHT -> readAll(WeightRecord::class).map { it.toPortRecord() }
            HealthConnectMetric.BODY_FAT -> readAll(BodyFatRecord::class).map { it.toPortRecord() }
        }

    override suspend fun changesToken(metric: HealthConnectMetric): String =
        client.getChangesToken(ChangesTokenRequest(setOf(metric.recordClass())))

    override suspend fun changes(
        metric: HealthConnectMetric,
        token: String,
    ): HealthConnectChangePage {
        val response = client.getChanges(token)
        return HealthConnectChangePage(
            changes =
                response.changes.mapNotNull { change ->
                    when (change) {
                        is UpsertionChange -> change.record.toPortRecordOrNull(metric)?.let(HealthConnectChange::Upsert)
                        is DeletionChange -> HealthConnectChange.Delete(change.recordId)
                        else -> null
                    }
                },
            nextToken = response.nextChangesToken,
            hasMore = response.hasMore,
            tokenExpired = response.changesTokenExpired,
        )
    }

    private suspend fun <T : Record> readAll(type: KClass<T>): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response =
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = type,
                        timeRangeFilter = TimeRangeFilter.before(Instant.now()),
                        pageToken = pageToken,
                    ),
                )
            records += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return records
    }

    private fun HealthConnectMetric.recordClass(): KClass<out Record> =
        when (this) {
            HealthConnectMetric.WEIGHT -> WeightRecord::class
            HealthConnectMetric.BODY_FAT -> BodyFatRecord::class
        }

    public companion object {
        public val WEIGHT_PERMISSION: String = HealthPermission.getReadPermission(WeightRecord::class)
        public val BODY_FAT_PERMISSION: String = HealthPermission.getReadPermission(BodyFatRecord::class)
        public val READ_PERMISSIONS: Set<String> = setOf(WEIGHT_PERMISSION, BODY_FAT_PERMISSION)
    }
}

private fun Record.toPortRecordOrNull(expected: HealthConnectMetric): HealthConnectRecord? =
    when (this) {
        is WeightRecord -> takeIf { expected == HealthConnectMetric.WEIGHT }?.toPortRecord()
        is BodyFatRecord -> takeIf { expected == HealthConnectMetric.BODY_FAT }?.toPortRecord()
        else -> null
    }

private fun WeightRecord.toPortRecord(): HealthConnectRecord =
    HealthConnectRecord(
        recordId = metadata.id,
        dataOriginPackage = metadata.dataOrigin.packageName,
        clientRecordId = metadata.clientRecordId,
        clientRecordVersion = metadata.clientRecordVersion,
        recordingMethod = metadata.recordingMethod,
        lastModifiedAtEpochMs = metadata.lastModifiedTime.toEpochMilli(),
        capturedAtEpochMs = time.toEpochMilli(),
        zoneOffsetSeconds = zoneOffset?.totalSeconds,
        metric = HealthConnectMetric.WEIGHT,
        canonicalValue = weight.inKilograms,
    )

private fun BodyFatRecord.toPortRecord(): HealthConnectRecord =
    HealthConnectRecord(
        recordId = metadata.id,
        dataOriginPackage = metadata.dataOrigin.packageName,
        clientRecordId = metadata.clientRecordId,
        clientRecordVersion = metadata.clientRecordVersion,
        recordingMethod = metadata.recordingMethod,
        lastModifiedAtEpochMs = metadata.lastModifiedTime.toEpochMilli(),
        capturedAtEpochMs = time.toEpochMilli(),
        zoneOffsetSeconds = zoneOffset?.totalSeconds,
        metric = HealthConnectMetric.BODY_FAT,
        canonicalValue = percentage.value,
    )
