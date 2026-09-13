package app.wlo.core.vault

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The `.wlo` container: a fixed-layout binary envelope around the (JSON)
 * versioned backup document. Layout (FORMAT.md is the normative spec):
 *
 * ```
 * offset  size  field
 * 0       4     magic "WLOB" (57 4C 4F 42)
 * 4       1     container formatVersion (0x01)
 * 5       1     kdfId (0x01 Argon2id | 0x02 PBKDF2)
 * 6       1     saltLen; then saltLen bytes of salt
 * ...     12    Argon2: memoryKiB(int32 BE) iterations(int32) parallelism(int32)
 *               PBKDF2: iterations(int32) padding(int32 zero) padding(int32 zero)
 * ...     1     nonceLen (12); then nonce bytes
 * ...     4     ciphertextLen (int32 BE); then ciphertext+GCM tag bytes
 * ```
 *
 * WHY binary-with-magic instead of a JSON wrapper: (1) the KDF params must be
 * readable BEFORE decryption (the key cannot be derived without them), and a
 * fixed layout makes that explicit instead of JSON-parsing an untrusted file
 * to learn how to parse it; (2) the magic byte-check turns "this is some
 * random file" into a clean [BackupContainerException.Reason.WRONG_MAGIC]
 * before any crypto work; (3) the inner payload stays plain JSON (the
 * documented, third-party-readable part) — the container only wraps it.
 *
 * AES-GCM (AESP-256, 12-byte random IV, 128-bit tag) over the whole JSON
 * document; the tag is what makes a wrong passphrase detectable — an
 * [AEADBadTagException] surfaces as [BackupContainerException.Reason.WRONG_PASSPHRASE],
 * never as garbage output. Data is protected only by the passphrase: the key
 * is derived from it every time and never persisted.
 */
public object BackupContainer {
    public val MAGIC: ByteArray = byteArrayOf(0x57, 0x4C, 0x4F, 0x42) // "WLOB"

    public const val FORMAT_VERSION: Int = 1

    public const val NONCE_BYTES: Int = 12

    public const val GCM_TAG_BITS: Int = 128

    private const val MAX_PLAUSIBLE_BYTES: Int = 256 * 1024 * 1024

    /** Encrypts [plaintextJson] under the passphrase-derived key. */
    public fun encrypt(
        plaintextJson: ByteArray,
        params: KdfParams,
        passphrase: CharArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray = encryptWithKey(plaintextJson, params, BackupKdf.deriveKey(params, passphrase), random)

    /**
     * Encrypts under a RAW 32-byte key — the auto-backup path, where the
     * stored key IS the passphrase-derived key for [params] (so the file
     * remains decryptable with the user's passphrase on any device; no
     * second KDF run happens here). Callers must zero [keyBytes].
     */
    public fun encryptWithKey(
        plaintextJson: ByteArray,
        params: KdfParams,
        keyBytes: ByteArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(params.salt.size == BackupKdf.SALT_BYTES) { "salt must be ${BackupKdf.SALT_BYTES} bytes" }
        require(keyBytes.size == BackupKdf.KEY_BYTES) { "key must be ${BackupKdf.KEY_BYTES} bytes" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintextJson)

        val out = ByteArrayOutputStream(MAGIC.size + 3 + params.salt.size + 12 + NONCE_BYTES + 4 + ciphertext.size)
        out.write(MAGIC)
        out.write(FORMAT_VERSION)
        out.write(params.kdfId.id.toInt())
        out.write(params.salt.size)
        out.write(params.salt)
        when (params) {
            is KdfParams.Argon2id -> {
                out.write(int32(params.memoryKiB))
                out.write(int32(params.iterations))
                out.write(int32(params.parallelism))
            }

            is KdfParams.Pbkdf2 -> {
                out.write(int32(params.iterations))
                out.write(int32(0)) // reserved
                out.write(int32(0)) // reserved
            }
        }
        out.write(NONCE_BYTES)
        out.write(nonce)
        out.write(int32(ciphertext.size))
        out.write(ciphertext)
        return out.toByteArray()
    }

    /**
     * Parses the container, derives the key from the header KDF params and
     * decrypts. Every structural problem throws [BackupContainerException]
     * with a typed reason — staged restore maps them to user-facing errors
     * and nothing is ever partially read.
     */
    public fun decrypt(
        container: ByteArray,
        passphrase: CharArray,
    ): DecryptedContainer {
        val reader = ByteReader(container)
        if (container.size < 12 || !reader.expect(MAGIC)) {
            throw BackupContainerException(BackupContainerException.Reason.WRONG_MAGIC, "not a WLO backup file")
        }
        val formatVersion = reader.uByte()
        if (formatVersion != FORMAT_VERSION) {
            throw BackupContainerException(
                BackupContainerException.Reason.UNSUPPORTED_FORMAT,
                "container format $formatVersion unsupported (this build reads v$FORMAT_VERSION)",
            )
        }
        val kdfId =
            KdfId.fromId(reader.byte().toByte())
                ?: throw BackupContainerException(BackupContainerException.Reason.UNSUPPORTED_FORMAT, "unknown kdfId")
        val saltLen = reader.uByte()
        val salt = reader.bytes(saltLen)
        val params =
            when (kdfId) {
                KdfId.ARGON2ID -> {
                    val memory = reader.int32()
                    val iterations = reader.int32()
                    val parallelism = reader.int32()
                    KdfParams.Argon2id(salt = salt, memoryKiB = memory, iterations = iterations, parallelism = parallelism)
                }

                KdfId.PBKDF2 -> {
                    val iterations = reader.int32()
                    reader.int32() // reserved
                    reader.int32() // reserved
                    KdfParams.Pbkdf2(salt = salt, iterations = iterations)
                }
            }
        val nonceLen = reader.uByte()
        val nonce = reader.bytes(nonceLen)
        val cipherLen = reader.int32()
        if (cipherLen !in 1..MAX_PLAUSIBLE_BYTES || cipherLen != reader.remaining()) {
            throw BackupContainerException(BackupContainerException.Reason.TRUNCATED, "ciphertext length $cipherLen does not match file")
        }
        val ciphertext = reader.bytes(cipherLen)

        val key = BackupKdf.deriveKey(params, passphrase)
        val cipher =
            try {
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
                }
            } finally {
                key.fill(0)
            }
        val plaintext =
            try {
                cipher.doFinal(ciphertext)
            } catch (tag: AEADBadTagException) {
                throw BackupContainerException(
                    BackupContainerException.Reason.WRONG_PASSPHRASE,
                    "authentication tag mismatch — wrong passphrase or corrupted file",
                )
            } catch (bad: Exception) {
                throw BackupContainerException(BackupContainerException.Reason.TRUNCATED, "ciphertext unusable: ${bad.message}")
            }
        return DecryptedContainer(formatVersion = formatVersion, kdfParams = params, plaintextJson = plaintext)
    }

    private fun int32(value: Int): ByteArray =
        byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )

    private class ByteReader(
        private val data: ByteArray,
    ) {
        private var pos: Int = 0

        fun expect(magic: ByteArray): Boolean {
            if (data.size < magic.size) return false
            for (i in magic.indices) if (data[pos + i] != magic[i]) return false
            pos += magic.size
            return true
        }

        fun byte(): Int = data[pos++].toInt()

        fun uByte(): Int = data[pos++].toInt() and 0xFF

        fun int32(): Int {
            val value =
                ((data[pos].toInt() and 0xFF) shl 24) or
                    ((data[pos + 1].toInt() and 0xFF) shl 16) or
                    ((data[pos + 2].toInt() and 0xFF) shl 8) or
                    (data[pos + 3].toInt() and 0xFF)
            pos += 4
            return value
        }

        fun bytes(n: Int): ByteArray {
            if (n < 0 || pos + n > data.size) {
                throw BackupContainerException(BackupContainerException.Reason.TRUNCATED, "file ends mid-header (wanted $n bytes)")
            }
            val out = data.copyOfRange(pos, pos + n)
            pos += n
            return out
        }

        fun remaining(): Int = data.size - pos
    }
}

/** A successfully decrypted container: header facts + the JSON document bytes. */
public data class DecryptedContainer(
    public val formatVersion: Int,
    public val kdfParams: KdfParams,
    public val plaintextJson: ByteArray,
)

/** Typed container failures (D8 spirit: reasons, not stack-trace archaeology). */
public class BackupContainerException(
    public val reason: Reason,
    message: String,
) : Exception(message) {
    public enum class Reason {
        WRONG_MAGIC,
        UNSUPPORTED_FORMAT,
        TRUNCATED,
        WRONG_PASSPHRASE,
    }
}
