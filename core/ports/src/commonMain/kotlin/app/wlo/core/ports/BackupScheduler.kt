package app.wlo.core.ports

/**
 * Export/backup scheduling (F13): what to assemble is common and tested; WHEN
 * to run is Android glue (WorkManager/alarms) behind this port — see
 * ARCHITECTURE.md §2.4 "Background work".
 */
public interface BackupScheduler {
    /**
     * Changes the persisted automatic-backup policy and reconciles scheduled
     * work with it. Enabling requires a destination; disabling cancels any
     * already-enqueued unique work before returning.
     */
    public suspend fun setEnabled(
        enabled: Boolean,
        request: BackupRequest? = null,
    )
}

/** A requested backup run; destination is a SAF tree uri string. */
public data class BackupRequest(
    public val destinationUri: String,
    public val includeVault: Boolean,
)
