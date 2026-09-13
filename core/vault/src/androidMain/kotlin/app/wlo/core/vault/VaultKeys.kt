package app.wlo.core.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * T-C3/T-F1 — the at-rest design, and the hard invariants it enforces
 * (F13 §9, quoted):
 *
 *  - "Every capture lands in app-private storage, invisible to the gallery
 *    and OS cloud-photo backup, by default" — blob files live under
 *    filesDir/vault/<partition>/ (app-private, never a media path), a
 *    `.nomedia` file anchors every partition dir against scanner accidents,
 *    and NO MediaStore/indexer registration API is ever called.
 *  - "Attachments never leave app-private storage" — the store exposes bytes
 *    only to in-process callers; export/backup inclusion rides R-U18's
 *    explicit opt-in at the BUNDLE layer, never a raw path out.
 *  - Partitions are PURPOSE-scoped (photo first — R-U14/R-U18 posture), each
 *    with its own AndroidKeyStore AES-256-GCM key (`wlo.vault.<partition>`),
 *    so wiping one category never needs another category's key and
 *    per-category purge (storage dashboard) is a key deletion + dir wipe.
 *  - Per-file AES-GCM: a fresh 12-byte IV per file, IV PREPENDED to the
 *    ciphertext (deterministic layout, no metadata side channel beyond the
 *    IV's own randomness). Filenames are random UUIDs — no user content in
 *    any path component; the opaque-name → logical-name mapping lives in the
 *    partition's `index` file (INSIDE the partition dir, app-private).
 *  - R-U16 honored structurally: nothing here writes or reads silhouette
 *    media — the silhouette pipeline derives vectors in memory (the vault
 *    would hold a *record*, via other storage, never a body image).
 *
 * Why not Tink (T-F4): we need exactly two primitives — Keystore AES-GCM
 * (javax.crypto, zero deps) and Argon2id (BouncyCastle, already pinned for
 * T-F2). Tink would add a ~2 MB facade to wrap calls we already drive
 * directly; Keystore keeps key custody hardware-backed, which is the part
 * Tink cannot do for us anyway.
 */
public object VaultKeys {
    /** Purpose-scoped partitions; `photo` is the v1 first tenant (M4 pipeline). */
    public enum class Partition(
        public val dirName: String,
        public val keyAlias: String,
    ) {
        PHOTO("photo", "wlo.vault.photo"),
        ;

        public companion object {
            public fun byDirName(name: String): Partition? = entries.firstOrNull { it.dirName == name }
        }
    }

    /** Gets-or-creates the partition's AndroidKeyStore AES-256-GCM key. */
    public fun partitionKey(alias: String): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Deletes the partition key (per-category purge leaves nothing to decrypt with). */
    public fun deleteKey(alias: String) {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keystore.containsAlias(alias)) keystore.deleteEntry(alias)
    }

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
}

/**
 * The per-file AES-GCM codec: random 12-byte IV, IV-prepended layout.
 * AndroidKeyStore keys generate the IV inside the hardware boundary, so
 * encrypt uses the cipher's own IV — callers never choose nonces.
 */
public object VaultCipher {
    public const val IV_BYTES: Int = 12

    public const val GCM_TAG_BITS: Int = 128

    public fun encrypt(
        key: SecretKey,
        plaintext: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv ?: error("Keystore produced no IV")
        require(iv.size == IV_BYTES) { "unexpected IV length ${iv.size}" }
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    public fun decrypt(
        key: SecretKey,
        blob: ByteArray,
    ): ByteArray {
        require(blob.size > IV_BYTES) { "blob too short to contain an IV" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob.copyOfRange(0, IV_BYTES)))
        return cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size))
    }
}

/**
 * One partition's file store: opaque UUID filenames, an in-partition index
 * (opaqueId → logicalName/contentType/timestamps — the ONLY place real names
 * exist), MediaStore-excluded by construction, with storage accounting
 * (F13 §3 storage dashboard queries: bytes + count per partition).
 */
public class VaultFileStore(
    context: Context,
) {
    private val root: File = File(context.applicationContext.filesDir, ROOT_DIR)

    public fun partitionDir(partition: VaultKeys.Partition): File {
        val dir = File(root, partition.dirName)
        if (!dir.exists()) {
            dir.mkdirs()
            // Belt and braces: app-private dirs are not scanned, but the
            // marker makes the exclusion explicit for any future path change.
            File(dir, NOMEDIA).writeBytes(ByteArray(0))
            File(root, NOMEDIA).writeBytes(ByteArray(0))
        }
        return dir
    }

    /** Stores bytes; returns the opaque id. Never stores the plaintext to disk. */
    public fun put(
        partition: VaultKeys.Partition,
        bytes: ByteArray,
        logicalName: String,
        contentType: String,
        atEpochMs: Long,
    ): String {
        val dir = partitionDir(partition)
        val key = VaultKeys.partitionKey(partition.keyAlias)
        val blob = VaultCipher.encrypt(key, bytes)
        val id =
            java.util.UUID
                .randomUUID()
                .toString()
        File(dir, "$id$BLOB_SUFFIX").writeBytes(blob)
        val index = readIndex(dir).toMutableMap()
        index[id] =
            VaultIndexEntry(
                logicalName = logicalName,
                contentType = contentType,
                sizeBytes = bytes.size.toLong(),
                createdAtEpochMs = atEpochMs,
            )
        writeIndex(dir, index)
        return id
    }

    /** Reads + decrypts; unknown ids throw [IllegalArgumentException]. */
    public fun get(
        partition: VaultKeys.Partition,
        id: String,
    ): Pair<VaultIndexEntry, ByteArray> {
        val dir = partitionDir(partition)
        val entry = readIndex(dir)[id] ?: throw IllegalArgumentException("no vault blob $id in ${partition.dirName}")
        val file = File(dir, "$id$BLOB_SUFFIX")
        val key = VaultKeys.partitionKey(partition.keyAlias)
        return entry to VaultCipher.decrypt(key, file.readBytes())
    }

    /** Deletes one blob (its bytes + its index line). */
    public fun delete(
        partition: VaultKeys.Partition,
        id: String,
    ) {
        val dir = partitionDir(partition)
        File(dir, "$id$BLOB_SUFFIX").delete()
        val index = readIndex(dir).toMutableMap()
        index.remove(id)
        writeIndex(dir, index)
    }

    /** F13 §3 storage dashboard: bytes + file count for a partition. */
    public fun accounting(partition: VaultKeys.Partition): VaultUsage {
        val dir = partitionDir(partition)
        val index = readIndex(dir)
        val bytes = dir.listFiles { f -> f.name.endsWith(BLOB_SUFFIX) }?.sumOf { it.length() } ?: 0L
        return VaultUsage(partition = partition.dirName, bytes = bytes, count = index.size)
    }

    /** Per-category purge: every blob + the key (leaves nothing decryptable). */
    public fun purgePartition(partition: VaultKeys.Partition) {
        val dir = partitionDir(partition)
        dir.listFiles()?.forEach { it.delete() }
        VaultKeys.deleteKey(partition.keyAlias)
    }

    private fun readIndex(dir: File): Map<String, VaultIndexEntry> {
        val file = File(dir, INDEX_FILE)
        if (!file.exists()) return emptyMap()
        return runCatching {
            app.wlo.core.documents.DocumentCodec.json
                .decodeFromString(VaultIndex.serializer(), file.readText())
                .entries
        }.getOrDefault(emptyMap())
    }

    private fun writeIndex(
        dir: File,
        index: Map<String, VaultIndexEntry>,
    ) {
        File(dir, INDEX_FILE).writeText(
            app.wlo.core.documents.DocumentCodec.json
                .encodeToString(VaultIndex.serializer(), VaultIndex(entries = index)),
        )
    }

    private companion object {
        const val ROOT_DIR = "vault"
        const val INDEX_FILE = "index"
        const val BLOB_SUFFIX = ".blob"
        const val NOMEDIA = ".nomedia"
    }
}

/** One index line: the mapping the opaque filename hides. */
@kotlinx.serialization.Serializable
public data class VaultIndexEntry(
    val logicalName: String,
    val contentType: String,
    val sizeBytes: Long,
    val createdAtEpochMs: Long,
)

@kotlinx.serialization.Serializable
public data class VaultIndex(
    val entries: Map<String, VaultIndexEntry> = emptyMap(),
)

/** Storage-accounting query result (F13 §3 storage dashboard). */
public data class VaultUsage(
    public val partition: String,
    public val bytes: Long,
    public val count: Int,
)
