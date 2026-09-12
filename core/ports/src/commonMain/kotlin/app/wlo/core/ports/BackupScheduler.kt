package app.wlo.core.ports

/**
 * Export/backup scheduling (F13): what to assemble is common and tested; WHEN
 * to run is Android glue (WorkManager/alarms) behind this port — see
 * ARCHITECTURE.md §2.4 "Background work".
 */
public interface BackupScheduler {
    public fun schedule(request: BackupRequest)
}

/** A requested backup run; destination is a SAF tree uri string. */
public data class BackupRequest(
    public val destinationUri: String,
    public val includeVault: Boolean,
)
