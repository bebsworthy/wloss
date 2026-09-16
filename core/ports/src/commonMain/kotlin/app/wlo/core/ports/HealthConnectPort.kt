package app.wlo.core.ports

/** F13's platform-neutral Health Connect boundary (WLO-0038). */
public interface HealthConnectPort {
    public suspend fun availability(): HealthConnectAvailability

    public suspend fun permissionState(): HealthConnectPermissionState

    /** Full history for the first import. A token acquired before this read catches concurrent writes. */
    public suspend fun readAll(metric: HealthConnectMetric): List<HealthConnectRecord>

    public suspend fun changesToken(metric: HealthConnectMetric): String

    public suspend fun changes(
        metric: HealthConnectMetric,
        token: String,
    ): HealthConnectChangePage
}

public enum class HealthConnectAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

public data class HealthConnectPermissionState(
    val weightGranted: Boolean,
    val bodyFatGranted: Boolean,
) {
    public val allGranted: Boolean get() = weightGranted && bodyFatGranted
    public val anyGranted: Boolean get() = weightGranted || bodyFatGranted
}

public enum class HealthConnectMetric(
    public val wireName: String,
) {
    WEIGHT("weight"),
    BODY_FAT("body-fat"),
}

/** Immutable upstream identity and value; canonicalValue is kg or percentage points. */
public data class HealthConnectRecord(
    val recordId: String,
    val dataOriginPackage: String,
    val clientRecordId: String?,
    val clientRecordVersion: Long?,
    val recordingMethod: Int,
    val lastModifiedAtEpochMs: Long,
    val capturedAtEpochMs: Long,
    val zoneOffsetSeconds: Int?,
    val metric: HealthConnectMetric,
    val canonicalValue: Double,
)

public sealed interface HealthConnectChange {
    public data class Upsert(
        val record: HealthConnectRecord,
    ) : HealthConnectChange

    public data class Delete(
        val recordId: String,
    ) : HealthConnectChange
}

public data class HealthConnectChangePage(
    val changes: List<HealthConnectChange>,
    val nextToken: String,
    val hasMore: Boolean,
    val tokenExpired: Boolean,
)

public data class HealthConnectImportLog(
    val atEpochMs: Long,
    val outcome: String,
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val skipped: Int = 0,
    val conflicts: Int = 0,
    val retryable: Boolean = false,
    val detail: String? = null,
)

public data class HealthConnectSyncStatus(
    val availability: HealthConnectAvailability,
    val permissions: HealthConnectPermissionState,
    val running: Boolean = false,
    val lastLog: HealthConnectImportLog? = null,
)

/** Manual-sync use case exposed to F13; background work intentionally remains separate. */
public interface HealthConnectSyncPort {
    public suspend fun status(): HealthConnectSyncStatus

    public suspend fun syncNow(profileId: String): HealthConnectImportLog
}
