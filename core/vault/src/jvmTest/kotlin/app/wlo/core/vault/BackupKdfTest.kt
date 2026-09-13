package app.wlo.core.vault

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * T-F2 KDF tests: the PUBLISHED RFC 9106 §5.3 Argon2id vector (run against
 * bcprov 1.86 — verified by hand before pinning), plus the R-U5 round-trip
 * and wrong-passphrase rejection semantics.
 */
class BackupKdfTest {
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun argon2id_matches_rfc9106_section_5_3_vector() {
        // RFC 9106 §5.3: Argon2id v=19, m=32 KiB, t=3, p=4, tag 32 bytes.
        val password = hex("01".repeat(32))
        val salt = hex("02".repeat(16))
        val secret = hex("03".repeat(8))
        val additional = hex("04".repeat(12))

        val builder =
            org.bouncycastle.crypto.params.Argon2Parameters
                .Builder(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id)
                .withVersion(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(32)
                .withIterations(3)
                .withParallelism(4)
                .withSalt(salt)
                .withSecret(secret)
                .withAdditional(additional)
        val generator =
            org.bouncycastle.crypto.generators
                .Argon2BytesGenerator()
        generator.init(builder.build())
        val out = ByteArray(32)
        generator.generateBytes(password, out)

        assertEquals(
            "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659",
            out.toHex(),
        )
    }

    @Test
    fun productionParams_produce32ByteKey_andAreDeterministicPerSalt() {
        val salt = "somesalt16bytes!".toByteArray(Charsets.UTF_8)
        val a = BackupKdf.deriveKey(BackupKdf.argon2idDefaults(salt), "correct horse".toCharArray())
        val b = BackupKdf.deriveKey(BackupKdf.argon2idDefaults(salt.copyOf()), "correct horse".toCharArray())
        assertEquals(BackupKdf.KEY_BYTES, a.size)
        assertContentEquals(a, b)
        val otherSalt = BackupKdf.argon2idDefaults("another salt 16!".toByteArray(Charsets.UTF_8))
        assertNotEquals(a.toHex(), BackupKdf.deriveKey(otherSalt, "correct horse".toCharArray()).toHex())
    }

    @Test
    fun container_wrongPassphrase_isRejectedNotGarbage() {
        val plaintext = """{"format":"wlo-backup"}""".toByteArray()
        val container =
            BackupContainer.encrypt(
                plaintext,
                BackupKdf.argon2idDefaults("somesalt16bytes!".toByteArray()),
                "open sesame".toCharArray(),
            )
        val failure =
            runCatching { BackupContainer.decrypt(container, "wrong passphrase".toCharArray()) }
                .exceptionOrNull()
        assertTrue(failure is BackupContainerException, "expected BackupContainerException, got $failure")
        assertEquals(BackupContainerException.Reason.WRONG_PASSPHRASE, failure!!.reason)
    }

    @Test
    fun pbkdf2Fallback_roundTrips() {
        val plaintext = ByteArray(2048) { it.toByte() }
        val params = BackupKdf.pbkdf2Defaults(ByteArray(BackupKdf.SALT_BYTES))
        val container = BackupContainer.encrypt(plaintext, params, "fallback".toCharArray())
        assertContentEquals(plaintext, BackupContainer.decrypt(container, "fallback".toCharArray()).plaintextJson)
    }
}
