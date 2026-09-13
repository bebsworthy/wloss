package app.wlo.core.vault

/**
 * A destination for backup files (F13 §3: an Android SAF folder the user
 * owns; point it at Syncthing/Nextcloud-backed storage and user-owned sync
 * comes free). Byte-level and testable: [RotationPolicy] runs against any
 * implementation, JVM tests use a fake, production uses [SafBackupStore].
 */
public interface BackupStore {
    /** Lists backup files currently in the destination, oldest first. */
    public suspend fun list(): List<BackupFileRef>

    /** Writes a new file atomically-enough (create-then-fill); returns its ref. */
    public suspend fun write(
        name: String,
        bytes: ByteArray,
    ): BackupFileRef

    /** Reads the whole file (backup files are single-digit MB at most). */
    public suspend fun read(ref: BackupFileRef): ByteArray

    /** Deletes one file (rotation only — restore never deletes). */
    public suspend fun delete(ref: BackupFileRef)
}

/** One backup file in a destination. */
public data class BackupFileRef(
    public val name: String,
    /** Opaque provider handle (a document uri for SAF); null for plain dirs. */
    public val handle: String? = null,
    public val sizeBytes: Long = 0,
)

/** Destination-level failure (refused creation, stream unavailable). */
public class BackupStoreException(
    message: String,
) : IllegalStateException(message)

/**
 * Destination factory (koin-verify-friendly binding; the Android impl wraps
 * the SAF tree uri string into a [SafBackupStore-like store]).
 */
public fun interface BackupStoreFactory {
    public fun create(destination: String): BackupStore
}

/**
 * F13 §3/R-U5 rotation: **write-new-then-rotate, keep 7** (default). Names are
 * `wlo_backup_<yyyy-MM-dd>.wlo`; multiple backups on one day supersede by
 * name (the fresh write replaces the same-day file's slot), so a burst of
 * manual backups never starves the daily window. Pure + unit-tested.
 */
public object RotationPolicy {
    public const val DEFAULT_KEEP: Int = 7

    public const val FILE_PREFIX: String = "wlo_backup_"

    public const val FILE_SUFFIX: String = ".wlo"

    /** The canonical backup file name for a date (ISO, yyyy-MM-dd). */
    public fun fileNameFor(isoDate: String): String = "$FILE_PREFIX$isoDate$FILE_SUFFIX"

    /** True for names this policy owns (rotation never touches foreign files). */
    public fun isBackupFile(name: String): Boolean = name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)

    /**
     * The files to delete after a successful write: all backup files EXCEPT
     * the [keep] lexicographically-newest (ISO dates sort chronologically).
     */
    public fun filesToRetire(
        names: List<String>,
        keep: Int = DEFAULT_KEEP,
    ): List<String> =
        names
            .filter(::isBackupFile)
            .distinct()
            .sorted()
            .dropLast(keep.coerceAtLeast(0))
}
