package app.wlo.core.ports

import kotlinx.coroutines.flow.Flow

/**
 * The F13 Data Vault's UI-facing port (F13 §3/§4 settings surfaces). Shape
 * note: this is the VIEW contract — the pipeline (assembly, container crypto,
 * staged restore, rotation) is implementation behind D1; `:app` binds this
 * port to it. Every operation is honest about failure: [VaultOperationException]
 * carries a [VaultFailure] reason the wizard renders verbatim, and staging
 * NEVER writes — a commit that was never staged is impossible by construction.
 */
public data class VaultPartitionUsage(
    public val partition: String,
    public val bytes: Long,
    public val count: Int,
)

/** One backup file in the destination (SAF listing). */
public data class VaultBackupFileInfo(
    public val name: String,
    public val sizeBytes: Long,
)

/** The backup posture as the controls screen renders it. */
public data class VaultBackupState(
    public val folderUri: String?,
    public val autoEnabled: Boolean,
    public val files: List<VaultBackupFileInfo>,
)

/** One completed backup run. */
public data class VaultBackupOutcome(
    public val fileName: String,
    public val sizeBytes: Long,
    public val retired: List<String>,
)

/** One section line of the staged restore report (F13 §4 "per-stage ticks"). */
public data class VaultRestoreSectionReport(
    public val name: String,
    public val rows: Int,
    public val ok: Boolean,
)

/**
 * The staged restore report shown BEFORE commit: counts per section, schema
 * migrated-from → migrated-to, and warnings. Nothing is applied yet.
 */
public data class VaultStagedRestoreReport(
    public val schemaVersionFrom: Int,
    public val schemaVersionTo: Int,
    public val sections: List<VaultRestoreSectionReport>,
    public val warnings: List<String>,
    public val totalRows: Int,
)

/** The post-commit accounting (F13 §3: insert vs keep-local). */
public data class VaultRestoreCommitReport(
    public val inserted: Int,
    public val skipped: Int,
    public val warnings: List<String>,
)

/** One written export file. */
public data class VaultExportOutcome(
    public val fileName: String,
    public val sizeBytes: Long,
)

/** A rendered CSV (the export preview; the SAF write happens on destination pick). */
public data class VaultCsvRender(
    public val csv: String,
    public val dataRows: Int,
    public val warnings: List<String>,
)

/** One proposed/edited CSV column mapping (R-S4 wizard line). */
public data class VaultCsvMappingView(
    public val sourceColumn: String,
    /** True = the column carries the day/date. */
    public val isDay: Boolean,
    /** Metric kind wire name ("weight", "intake", "custom", …); null for day columns. */
    public val kind: String?,
    public val unit: String?,
    /** EAV custom-metric name when [kind] == "custom". */
    public val customName: String?,
)

/** The CSV table header/shape before mapping. */
public data class VaultCsvPreview(
    public val columns: List<String>,
    public val rowCount: Int,
    public val proposed: List<VaultCsvMappingView>,
)

/** The staged CSV import: rows parsed + per-row skip warnings (never fatal). */
public data class VaultCsvStagedReport(
    public val stagedRows: Int,
    public val warnings: List<String>,
)

/** Fresh Start (R-B7): what the hide-not-delete pass archived. */
public data class VaultFreshStartReport(
    public val archivedDiaryEntries: Int,
)

/** Typed, renderable failures (D8 spirit: reasons, not stack-trace archaeology). */
public enum class VaultFailure {
    /** Not a WLO container (magic mismatch) or not a WLO export bundle. */
    WRONG_FORMAT,

    /** Container/bundle version this build cannot read. */
    UNSUPPORTED_FORMAT,

    /** Newer than this build's schema (forward version). */
    FUTURE_VERSION,

    /** File ends mid-header / ciphertext unusable. */
    TRUNCATED,

    /** Authentication tag mismatch — wrong passphrase (or corrupted file). */
    WRONG_PASSPHRASE,

    /** A row/section fails its schema; nothing was staged. */
    SCHEMA_INVALID,

    /** Rows reference profiles/plans/entries missing from backup AND device. */
    REFERENCE_BROKEN,

    /** Header malformed (missing markers/sections). */
    BAD_HEADER,

    /** No backup folder chosen yet (backup-now has nowhere to write). */
    NO_BACKUP_FOLDER,

    /** No stored auto-backup key (scheduled run before first setup). */
    NO_AUTO_KEY,

    /** The destination/storage layer refused the operation. */
    IO,
}

public class VaultOperationException(
    public val failure: VaultFailure,
    detail: String,
) : Exception(detail)

/**
 * The F13 data mechanics as one door (F13 §3 export formats + §4 flows 1/2/3;
 * Fresh Start R-B7). Staging discipline: [stageRestore] and [stageCsvImport]
 * only REPORT; [commitStaged] applies the last staged payload atomically —
 * a commit without a staging in this session fails, so a hostile file can
 * never reach the stores through a stale or missing stage.
 */
public interface DataVaultPort {
    // --- storage dashboard (F13 §3) ---------------------------------------

    /** Per-partition bytes + blob counts (encrypted at rest, opaque names). */
    public suspend fun storageUsage(): List<VaultPartitionUsage>

    /** Per-category purge: blobs + the partition key (nothing left to decrypt). */
    public suspend fun reclaimPartition(partition: String)

    // --- backup controls (F13 §3, R-U5 rotation 7) --------------------------

    /** Folder + auto toggle + the rotated file list (newest named last). */
    public suspend fun backupState(): VaultBackupState

    /**
     * Runs a full backup into the chosen SAF folder. [passphrase] non-null =
     * user-initiated passphrase run; null = the stored auto-backup key
     * (scheduled semantics; fails [VaultFailure.NO_AUTO_KEY] when never set up).
     */
    public suspend fun backupNow(passphrase: CharArray?): VaultBackupOutcome

    /**
     * Arms auto-backup: derives + wraps the passphrase-equivalent key into
     * device-bound custody (the R-U5 unattended-run shape — the FILE stays
     * passphrase-decryptable on any device; the wrapped key does not travel).
     * The passphrase itself is never stored anywhere.
     */
    public suspend fun configureAutoBackupKey(passphrase: CharArray)

    /** True when an auto-backup key is configured (device-bound custody). */
    public suspend fun autoBackupKeyConfigured(): Boolean

    // --- staged restore (F13 §4 flow 1/3) -----------------------------------

    /**
     * Decrypts + verifies + decodes a `.wlo` container into the staged report.
     * Throws [VaultOperationException]; NO WRITE happens here.
     */
    public suspend fun stageRestore(
        bytes: ByteArray,
        passphrase: CharArray,
    ): VaultStagedRestoreReport

    /**
     * Stages a PLAINTEXT `wlo-export` JSON bundle (F13 §3's second format;
     * R-U5 makes plaintext the explicit export choice, so import accepts it
     * back). An encrypted container passed here fails with
     * [VaultFailure.WRONG_PASSPHRASE] and copy pointing at the restore wizard.
     */
    public suspend fun stageBundleImport(bytes: ByteArray): VaultStagedRestoreReport

    /** Applies the last staged restore in one transaction. */
    public suspend fun commitStaged(): VaultRestoreCommitReport

    /** True while a staged restore waits for its explicit confirm. */
    public fun stagedRestorePending(): Flow<Boolean>

    // --- export (F13 §3 formats; secrets blanked) ----------------------------

    /** Renders the versioned JSON bundle (plaintext BY CHOICE per R-U5). */
    public suspend fun renderJsonBundle(): ByteArray

    /** Renders the per-metric CSV grid (one row per event, R-B8). */
    public suspend fun renderMetricCsv(): VaultCsvRender

    /** Writes rendered bytes to the user-picked SAF destination. */
    public suspend fun writeToDestination(
        destination: String,
        fileName: String,
        bytes: ByteArray,
    ): VaultExportOutcome

    // --- import (F13 §3: generic CSV + column mapping, R-S4) -----------------

    /** Parses a candidate CSV into its header/shape + a sniffed mapping. */
    public suspend fun previewCsv(bytes: ByteArray): VaultCsvPreview

    /**
     * Stages a CSV import under the (user-corrected) mapping — rows parsed,
     * weak rows skipped WITH reasons. Nothing is written.
     */
    public suspend fun stageCsvImport(
        bytes: ByteArray,
        mapping: List<VaultCsvMappingView>,
    ): VaultCsvStagedReport

    /** Applies the last staged CSV import. */
    public suspend fun commitCsvImport(): VaultCsvStagedReport

    // --- Fresh Start (R-B7; the F13 mechanics half of F01's ritual) ----------

    /**
     * Counts what a Fresh Start would hide (diary entries; history is
     * archived, never deleted — backups keep everything).
     */
    public suspend fun freshStartPreview(): VaultFreshStartReport

    /** Performs the hide: archives diary history + retires the active profile. */
    public suspend fun freshStartHide(): VaultFreshStartReport
}
