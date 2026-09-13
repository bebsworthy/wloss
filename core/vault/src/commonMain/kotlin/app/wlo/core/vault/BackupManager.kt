package app.wlo.core.vault

import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId

/**
 * The F13 §3 backup pipeline in one door: assemble → encode(+manifest) → KDF →
 * container → store → rotate (keep 7, R-U5). [backupNow] is what both the
 * manual button (PART B) and [BackupWorker] call. The restore trip lives in
 * [StagedRestorer]/[RestoreCommitter] — PART B stages, shows the report, then
 * commits (F13 §4 flow 1).
 *
 * The passphrase is NEVER persisted. Unattended (WorkManager) runs use the
 * derived key re-wrapped by the platform keystore — see androidMain's
 * [AutoBackupKeyVault] behind [AutoBackupKeyProvider]: the backup FILE stays
 * passphrase-only-protected (it travels to the user's folder), the wrapped
 * key is device-bound (it does not travel), so losing the phone never loses
 * the backup's secrecy while auto-backup still runs without re-prompting.
 * FLAG for ADR-008: this is the only workable shape for encrypted-by-default
 * (R-U5) UNATTENDED backups; the alternative (plaintext scheduled backups)
 * contradicts R-U5.
 */

public interface AutoBackupKeyProvider {
    /**
     * Device-bound custody of the auto-backup key, inverted for commonMain:
     * the Android side implements it with AndroidKeyStore (AutoBackupKeyVault).
     * The stored passphrase-equivalent key + its KDF params, or null when not
     * set up.
     */
    public suspend fun load(): StoredAutoKey?
}

/** The unwrapped auto-backup key (callers zero [key] after use). */
public class StoredAutoKey(
    public val key: ByteArray,
    public val params: KdfParams,
)

/** Wall-clock port for backup timestamps (verify-friendly; no Function0 in the graph). */
public fun interface EpochClock {
    public fun nowEpochMs(): Long
}

public class BackupManager(
    private val assembler: SnapshotAssembler,
    private val storeFactory: BackupStoreFactory,
    private val autoKeys: AutoBackupKeyProvider?,
    private val clock: EpochClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    public data class BackupOutcome(
        public val fileName: String,
        public val sizeBytes: Long,
        public val retired: List<String>,
    )

    /**
     * Full backup run into [destination] (the SAF tree uri string).
     * [passphrase] null = use the stored auto-backup key (scheduled runs);
     * non-null = user-initiated with the typed passphrase.
     */
    public suspend fun backupNow(
        destination: String,
        passphrase: CharArray?,
        options: BackupOptions = BackupOptions(),
    ): BackupOutcome {
        val payload = assembler.assemble(options)
        val createdAt = clock.nowEpochMs()
        val json = BackupCodec.encode(payload, createdAt)
        val salt = ByteArray(BackupKdf.SALT_BYTES).also(SecureRandom()::nextBytes)
        val store = storeFactory.create(destination)

        val container =
            if (passphrase != null) {
                val params = BackupKdf.argon2idDefaults(salt)
                BackupContainer.encrypt(json, params, passphrase)
            } else {
                // Unattended run: the stored key IS the passphrase-derived key
                // for the recorded salt — the file stays passphrase-decryptable.
                val stored =
                    autoKeys?.load()
                        ?: throw BackupStoreException("no auto-backup key stored — set up backups with a passphrase first")
                try {
                    BackupContainer.encryptWithKey(json, stored.params, stored.key)
                } finally {
                    stored.key.fill(0)
                }
            }

        // Java-8 APIs only (ADR-002: minSdk 29 has no java.time 9+ methods).
        val name =
            RotationPolicy.fileNameFor(
                Instant
                    .ofEpochMilli(createdAt)
                    .atZone(zone)
                    .toLocalDate()
                    .toString(),
            )
        val ref = store.write(name, container)

        // Write-new-then-rotate (R-U5 default keep 7): retire only AFTER the
        // new file is durably in place — a crash mid-run can never leave the
        // destination with fewer good backups than it had.
        val survivors = store.list().map { it.name }
        val retiredNames = RotationPolicy.filesToRetire(survivors)
        val retired =
            retiredNames
                .mapNotNull { target -> store.list().firstOrNull { it.name == target } }
                .onEach { ref -> store.delete(ref) }
                .map { it.name }

        return BackupOutcome(fileName = ref.name, sizeBytes = ref.sizeBytes, retired = retired)
    }
}
