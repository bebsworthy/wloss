package app.wlo.core.vault

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * T-F2 — the backup KDF (R-U5: backups are passphrase-encrypted by default).
 *
 * Decision: **Argon2id** via BouncyCastle's lightweight API
 * (`org.bouncycastle.crypto.generators.Argon2BytesGenerator`, verified present
 * in bcprov-jdk18on 1.86 on Maven Central, checked 2026-09-12). Why not the
 * alternatives:
 *  - javax.crypto ships no memory-hard KDF (PBKDF2 only);
 *  - Tink (T-F4) would add a ~2 MB facade to use exactly two primitives we
 *    already drive directly (Keystore AES-GCM + this KDF);
 *  - BC's JCA provider registration is NOT needed — the generator is a plain
 *    class, keeping the crypto surface explicit and reviewable.
 *
 * Parameters (mobile justification): m=32768 KiB (32 MiB), t=2, p=1, 16-byte
 * salt, 32-byte key. 32 MiB is the OWASP mobile-tier pick: ~100–300 ms on a
 * mid-range phone without OOM risk on 2 GiB devices; t=2 because Argon2id's
 * hybrid filling already resists GPU attacks well at low passes. PBKDF2
 * (PBKDF2WithHmacSHA256, 600k iterations — OWASP 2023 guidance) remains in
 * the container format as `kdfId=PBKDF2` so old files stay readable if the
 * Argon2 path is ever found problematic on a specific vendor keystore/ROM.
 */
public object BackupKdf {
    /** 32 MiB in KiB — see class KDoc for the mobile justification. */
    public const val ARGON2_MEMORY_KIB: Int = 32_768

    public const val ARGON2_ITERATIONS: Int = 2

    public const val ARGON2_PARALLELISM: Int = 1

    public const val SALT_BYTES: Int = 16

    public const val KEY_BYTES: Int = 32

    /** PBKDF2 fallback iteration count (OWASP 2023 guidance for SHA-256). */
    public const val PBKDF2_ITERATIONS: Int = 600_000

    public fun argon2idDefaults(salt: ByteArray): KdfParams =
        KdfParams.Argon2id(
            memoryKiB = ARGON2_MEMORY_KIB,
            iterations = ARGON2_ITERATIONS,
            parallelism = ARGON2_PARALLELISM,
            salt = salt,
        )

    public fun pbkdf2Defaults(salt: ByteArray): KdfParams = KdfParams.Pbkdf2(iterations = PBKDF2_ITERATIONS, salt = salt)

    /** Derives the 32-byte AES-256 key from the passphrase (never stored). */
    public fun deriveKey(
        params: KdfParams,
        passphrase: CharArray,
    ): ByteArray =
        when (params) {
            is KdfParams.Argon2id -> deriveArgon2id(params, passphrase)
            is KdfParams.Pbkdf2 -> derivePbkdf2(params, passphrase)
        }

    private fun deriveArgon2id(
        params: KdfParams.Argon2id,
        passphrase: CharArray,
    ): ByteArray {
        val builder =
            Argon2Parameters
                .Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .withSalt(params.salt)
        val generator = Argon2BytesGenerator()
        generator.init(builder.build())
        val out = ByteArray(KEY_BYTES)
        generator.generateBytes(passphrase, out)
        return out
    }

    private fun derivePbkdf2(
        params: KdfParams.Pbkdf2,
        passphrase: CharArray,
    ): ByteArray {
        val spec =
            PBEKeySpec(
                passphrase,
                params.salt,
                params.iterations,
                KEY_BYTES * 8,
            )
        return SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }
}

/** KDF description carried in the container header (params readable pre-decrypt). */
public sealed interface KdfParams {
    public val salt: ByteArray

    public val kdfId: KdfId

    public data class Argon2id(
        override val salt: ByteArray,
        val memoryKiB: Int,
        val iterations: Int,
        val parallelism: Int,
    ) : KdfParams {
        override val kdfId: KdfId get() = KdfId.ARGON2ID

        override fun equals(other: Any?): Boolean =
            other is Argon2id &&
                salt.contentEquals(other.salt) &&
                memoryKiB == other.memoryKiB &&
                iterations == other.iterations &&
                parallelism == other.parallelism

        override fun hashCode(): Int = salt.contentHashCode() * 31 + iterations
    }

    public data class Pbkdf2(
        override val salt: ByteArray,
        val iterations: Int,
    ) : KdfParams {
        override val kdfId: KdfId get() = KdfId.PBKDF2

        override fun equals(other: Any?): Boolean = other is Pbkdf2 && salt.contentEquals(other.salt) && iterations == other.iterations

        override fun hashCode(): Int = salt.contentHashCode() * 31 + iterations
    }
}

/** The KDF identifier byte in the container header (FORMAT.md §2). */
public enum class KdfId(
    public val id: Byte,
) {
    ARGON2ID(0x01),
    PBKDF2(0x02),
    ;

    public companion object {
        public fun fromId(id: Byte): KdfId? = entries.firstOrNull { it.id == id }
    }
}
