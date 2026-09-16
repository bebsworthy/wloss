package app.wlo.core.vault

import androidx.room3.withWriteTransaction
import app.wlo.core.common.WloResult
import app.wlo.core.data.WeighInRepository
import app.wlo.core.database.HealthConnectImportLogEntity
import app.wlo.core.database.HealthConnectRecordEntity
import app.wlo.core.database.HealthConnectSyncStateEntity
import app.wlo.core.database.MeasurementEventEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.ports.HealthConnectAvailability
import app.wlo.core.ports.HealthConnectChange
import app.wlo.core.ports.HealthConnectImportLog
import app.wlo.core.ports.HealthConnectMetric
import app.wlo.core.ports.HealthConnectPermissionState
import app.wlo.core.ports.HealthConnectPort
import app.wlo.core.ports.HealthConnectRecord
import app.wlo.core.ports.HealthConnectSyncPort
import app.wlo.core.ports.HealthConnectSyncStatus
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/**
 * Identity-driven manual synchronization. The durable token advances only in
 * the same Room transaction as its page, making replay safe after a process
 * death. Distinct Health Connect IDs always remain distinct WLO events.
 */
public class HealthConnectSyncManager(
    private val client: HealthConnectPort,
    private val db: WloDatabase,
    private val weighIns: WeighInRepository,
    private val nowEpochMs: () -> Long,
) : HealthConnectSyncPort {
    override suspend fun status(): HealthConnectSyncStatus {
        val availability = client.availability()
        val permissions =
            if (availability == HealthConnectAvailability.AVAILABLE) {
                client.permissionState()
            } else {
                HealthConnectPermissionState(weightGranted = false, bodyFatGranted = false)
            }
        val latest = db.profiles().active()?.let { db.healthConnect().latestLog(it.id)?.toDomain() }
        return HealthConnectSyncStatus(availability, permissions, lastLog = latest)
    }

    override suspend fun syncNow(profileId: String): HealthConnectImportLog {
        val availability = client.availability()
        if (availability != HealthConnectAvailability.AVAILABLE) {
            return log(profileId, "unavailable", detail = availability.name.lowercase())
        }
        val permissions = client.permissionState()
        if (!permissions.anyGranted) return log(profileId, "permission-revoked", detail = "No read access")

        return try {
            val total = MutableCounts()
            if (permissions.weightGranted) syncMetric(profileId, HealthConnectMetric.WEIGHT, total)
            if (permissions.bodyFatGranted) syncMetric(profileId, HealthConnectMetric.BODY_FAT, total)
            log(profileId, "success", total)
        } catch (error: Exception) {
            log(profileId, "retry", retryable = true, detail = error.message ?: error::class.simpleName)
        }
    }

    private suspend fun syncMetric(
        profileId: String,
        metric: HealthConnectMetric,
        total: MutableCounts,
    ) {
        val prior = db.healthConnect().syncState(profileId, metric.wireName)
        if (prior == null) {
            fullReconcile(profileId, metric, total)
            return
        }
        var token = prior.changeToken
        while (true) {
            val page = client.changes(metric, token)
            if (page.tokenExpired) {
                fullReconcile(profileId, metric, total)
                return
            }
            db.withWriteTransaction {
                applyChanges(profileId, page.changes, total)
                db.healthConnect().upsertSyncState(
                    HealthConnectSyncStateEntity(profileId, metric.wireName, page.nextToken, nowEpochMs()),
                )
            }
            token = page.nextToken
            if (!page.hasMore) return
        }
    }

    /** Token-before-read catches writes racing the initial full-history read. */
    private suspend fun fullReconcile(
        profileId: String,
        metric: HealthConnectMetric,
        total: MutableCounts,
    ) {
        var token = client.changesToken(metric)
        val records = client.readAll(metric)
        db.withWriteTransaction {
            val upstreamIds = records.mapTo(mutableSetOf()) { it.recordId }
            db
                .healthConnect()
                .records(profileId, metric.wireName)
                .filterNot { it.recordId in upstreamIds }
                .forEach { deleteRecord(it, total) }
            records.forEach { upsertRecord(profileId, it, total) }
        }
        while (true) {
            val page = client.changes(metric, token)
            if (page.tokenExpired) throw IllegalStateException("Health Connect token expired during full reconciliation")
            db.withWriteTransaction {
                applyChanges(profileId, page.changes, total)
                db.healthConnect().upsertSyncState(
                    HealthConnectSyncStateEntity(profileId, metric.wireName, page.nextToken, nowEpochMs()),
                )
            }
            token = page.nextToken
            if (!page.hasMore) return
        }
    }

    private suspend fun applyChanges(
        profileId: String,
        changes: List<HealthConnectChange>,
        counts: MutableCounts,
    ) {
        changes.forEach { change ->
            when (change) {
                is HealthConnectChange.Upsert -> upsertRecord(profileId, change.record, counts)
                is HealthConnectChange.Delete -> {
                    val existing = db.healthConnect().record(change.recordId)
                    if (existing == null) counts.skipped++ else deleteRecord(existing, counts)
                }
            }
        }
    }

    private suspend fun upsertRecord(
        profileId: String,
        record: HealthConnectRecord,
        counts: MutableCounts,
    ) {
        require(record.canonicalValue.isFinite() && record.canonicalValue > 0.0) { "Invalid ${record.metric.wireName}" }
        val existing = db.healthConnect().record(record.recordId)
        if (existing != null && existing.samePayload(record)) {
            counts.skipped++
            return
        }
        if (existing != null && existing.profileId != profileId) {
            counts.conflicts++
            return
        }
        val eventId =
            when (record.metric) {
                HealthConnectMetric.WEIGHT -> {
                    if (existing != null) requireOk(weighIns.deleteWeighIn(existing.measurementEventId, record.instant()))
                    requireOk(
                        weighIns.appendWeighIn(
                            profileId = profileId,
                            dayEpochDay = record.epochDay(),
                            weightKg = record.canonicalValue,
                            capturedAt = record.instant(),
                            source = MeasurementSource.HEALTH_CONNECT,
                        ),
                    ).event.id
                }
                HealthConnectMetric.BODY_FAT -> {
                    if (existing != null) {
                        db.measurementEventAttrs().deleteForEvent(existing.measurementEventId)
                        db.measurementEvents().deleteById(existing.measurementEventId)
                    }
                    val id = Uuid.random().toString()
                    db.measurementEvents().insert(
                        MeasurementEventEntity(
                            id = id,
                            profileId = profileId,
                            dayEpochDay = record.epochDay(),
                            kind = MeasurementKind.BODY_FAT.wireName,
                            valueReal = record.canonicalValue,
                            unit = MeasurementKind.BODY_FAT.unit,
                            source = MeasurementSource.HEALTH_CONNECT,
                            capturedAtEpochMs = record.capturedAtEpochMs,
                        ),
                    )
                    id
                }
            }
        db.healthConnect().upsertRecord(record.toEntity(profileId, eventId))
        if (existing == null) counts.inserted++ else counts.updated++
    }

    private suspend fun deleteRecord(
        existing: HealthConnectRecordEntity,
        counts: MutableCounts,
    ) {
        if (existing.metric == HealthConnectMetric.WEIGHT.wireName) {
            requireOk(weighIns.deleteWeighIn(existing.measurementEventId, Instant.fromEpochMilliseconds(nowEpochMs())))
        } else {
            db.measurementEventAttrs().deleteForEvent(existing.measurementEventId)
            db.measurementEvents().deleteById(existing.measurementEventId)
        }
        db.healthConnect().deleteRecord(existing.recordId)
        counts.deleted++
    }

    private suspend fun log(
        profileId: String,
        outcome: String,
        counts: MutableCounts = MutableCounts(),
        retryable: Boolean = false,
        detail: String? = null,
    ): HealthConnectImportLog {
        val log =
            HealthConnectImportLog(
                nowEpochMs(),
                outcome,
                counts.inserted,
                counts.updated,
                counts.deleted,
                counts.skipped,
                counts.conflicts,
                retryable,
                detail,
            )
        db.healthConnect().appendLog(log.toEntity(profileId))
        return log
    }
}

private data class MutableCounts(
    var inserted: Int = 0,
    var updated: Int = 0,
    var deleted: Int = 0,
    var skipped: Int = 0,
    var conflicts: Int = 0,
)

private fun HealthConnectRecord.instant(): Instant = Instant.fromEpochMilliseconds(capturedAtEpochMs)

private fun HealthConnectRecord.epochDay(): Long {
    val zone: TimeZone = zoneOffsetSeconds?.let { FixedOffsetTimeZone(UtcOffset(seconds = it)) } ?: TimeZone.UTC
    return instant()
        .toLocalDateTime(zone)
        .date
        .toEpochDays()
        .toLong()
}

private fun HealthConnectRecordEntity.samePayload(record: HealthConnectRecord): Boolean =
    lastModifiedAtEpochMs == record.lastModifiedAtEpochMs &&
        clientRecordVersion == record.clientRecordVersion &&
        capturedAtEpochMs == record.capturedAtEpochMs &&
        canonicalValue == record.canonicalValue &&
        metric == record.metric.wireName

private fun HealthConnectRecord.toEntity(
    profileId: String,
    eventId: String,
): HealthConnectRecordEntity =
    HealthConnectRecordEntity(
        recordId,
        profileId,
        eventId,
        dataOriginPackage,
        clientRecordId,
        clientRecordVersion,
        recordingMethod,
        lastModifiedAtEpochMs,
        capturedAtEpochMs,
        zoneOffsetSeconds,
        metric.wireName,
        canonicalValue,
    )

private fun HealthConnectImportLog.toEntity(profileId: String): HealthConnectImportLogEntity =
    HealthConnectImportLogEntity(
        Uuid.random().toString(),
        profileId,
        atEpochMs,
        outcome,
        inserted,
        updated,
        deleted,
        skipped,
        conflicts,
        retryable,
        detail,
    )

private fun HealthConnectImportLogEntity.toDomain(): HealthConnectImportLog =
    HealthConnectImportLog(atEpochMs, outcome, inserted, updated, deleted, skipped, conflicts, retryable, detail)

private fun <T> requireOk(result: WloResult<T>): T =
    when (result) {
        is WloResult.Ok -> result.value
        is WloResult.Err -> throw IllegalStateException(result.error.toString())
    }
