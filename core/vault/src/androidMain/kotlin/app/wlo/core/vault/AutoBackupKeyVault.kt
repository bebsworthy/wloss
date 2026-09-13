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
 * Device-bound custody for the AUTO-BACKUP key (R-U5 unattended runs).
 *
 * The passphrase itself is never stored anywhere. On backup setup the
 * passphrase-derived key is wrapped (AES-GCM) by an AndroidKeyStore key that
 * never leaves the hardware/TEE; the wrapped blob + the KDF salt live in
 * app-private storage and die with the device (or the app's data). A fresh
 * install on a new phone cannot read them — the user types the passphrase
 * there, which is exactly the R-U5 lockout-warning contract.
 *
 * Deliberately separate from the vault PARTITION keys ([VaultKeys]): those
 * protect files at rest on this device; this protects a derived key whose
 * only job is re-derivation-free scheduled backups.
 */
public class AutoBackupKeyVault(
    context: Context,
) : AutoBackupKeyProvider {
    private val file: File = File(context.applicationContext.filesDir, WRAPPED_KEY_FILE)

    /** Derives + wraps + stores, replacing any previous auto-backup key. */
    public fun store(
        passphrase: CharArray,
        salt: ByteArray,
    ) {
        val params = BackupKdf.argon2idDefaults(salt)
        val derived = BackupKdf.deriveKey(params, passphrase)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val wrapped = cipher.doFinal(derived)
        derived.fill(0)
        val iv = cipher.iv
        // Layout: [wrappedLen int32][ivLen int32][wrapped][iv][salt].
        val out =
            ByteArray(8 + wrapped.size + iv.size + salt.size).apply {
                putInt(0, wrapped.size)
                putInt(4, iv.size)
                wrapped.copyInto(this, 8)
                iv.copyInto(this, 8 + wrapped.size)
                salt.copyInto(this, 8 + wrapped.size + iv.size)
            }
        file.writeBytes(out)
    }

    /** Unwraps the stored key, or null when auto-backup was never set up. */
    override suspend fun load(): StoredAutoKey? {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.size < 16) return null
        val wrappedLen = bytes.intAt(0)
        val ivLen = bytes.intAt(4)
        // store() layout: [wrappedLen][ivLen][wrapped][iv][SALT] — the salt
        // rides after the blob and must be counted or load() always bails.
        if (bytes.size != 8 + wrappedLen + ivLen + BackupKdf.SALT_BYTES) return null
        val wrapped = bytes.copyOfRange(8, 8 + wrappedLen)
        val iv = bytes.copyOfRange(8 + wrappedLen, 8 + wrappedLen + ivLen)
        // The recorded salt is the one the user's passphrase derives with, so
        // scheduled writes and passphrase restores land on the same key.
        val salt =
            runCatching { fileSalt() }.getOrNull()
                ?: return null
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = keystore.getKey(KEY_ALIAS, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val unwrapped =
            runCatching { cipher.doFinal(wrapped) }.getOrElse { return null }
        return StoredAutoKey(key = unwrapped, params = BackupKdf.argon2idDefaults(salt))
    }

    /** Removes the wrapped key ("turn off auto-backup" / revoke everything). */
    public fun clear() {
        file.delete()
    }

    /** The salt rides the same file (after the wrapped+iv blob), 16 bytes. */
    private fun fileSalt(): ByteArray {
        val bytes = file.readBytes()
        val wrappedLen = bytes.intAt(0)
        val ivLen = bytes.intAt(4)
        return bytes.copyOfRange(8 + wrappedLen + ivLen, 8 + wrappedLen + ivLen + BackupKdf.SALT_BYTES)
    }

    private fun keystoreKey(): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.putInt(
        offset: Int,
        value: Int,
    ) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    private fun ByteArray.intAt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "wlo.autobackup.wrap"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val WRAPPED_KEY_FILE = "autobackup.wrapped.key"
    }
}
