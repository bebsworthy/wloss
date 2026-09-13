package app.wlo.core.vault

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Container layout (FORMAT.md §2) + typed hostile-file rejection. */
class BackupContainerTest {
    private val passphrase = "correct horse battery".toCharArray()
    private val salt = "0123456789abcdef".toByteArray(Charsets.UTF_8)

    private fun encryptSample(): ByteArray =
        BackupContainer.encrypt(
            plaintextJson = SAMPLE_JSON.toByteArray(),
            params = BackupKdf.argon2idDefaults(salt),
            passphrase = passphrase,
            random = java.security.SecureRandom(byteArrayOf(7)), // deterministic nonces
        )

    @Test
    fun roundTrip_preservesBytes() {
        val container = encryptSample()
        assertTrue(container.contentToString().let { container.size > 40 })
        val decrypted = BackupContainer.decrypt(container, passphrase)
        assertContentEquals(SAMPLE_JSON.toByteArray(), decrypted.plaintextJson)
        assertEquals(BackupContainer.FORMAT_VERSION, decrypted.formatVersion)
        assertEquals(KdfId.ARGON2ID, decrypted.kdfParams.kdfId)
        val params = decrypted.kdfParams as KdfParams.Argon2id
        assertEquals(BackupKdf.ARGON2_MEMORY_KIB, params.memoryKiB)
        assertContentEquals(salt, params.salt)
    }

    @Test
    fun magic_isWlob() {
        val container = encryptSample()
        assertContentEquals(byteArrayOf(0x57, 0x4C, 0x4F, 0x42), container.copyOfRange(0, 4))
    }

    @Test
    fun wrongMagic_isRejectedBeforeCrypto() {
        val junk = "this is somebody else's file, definitely not WLO".toByteArray()
        val failure = assertFailsWith<BackupContainerException> { BackupContainer.decrypt(junk, passphrase) }
        assertEquals(BackupContainerException.Reason.WRONG_MAGIC, failure.reason)
    }

    @Test
    fun truncatedFile_isRejected() {
        val container = encryptSample()
        val failure =
            assertFailsWith<BackupContainerException> {
                BackupContainer.decrypt(container.copyOfRange(0, container.size / 2), passphrase)
            }
        assertTrue(failure.reason == BackupContainerException.Reason.TRUNCATED, "got ${failure.reason}")
    }

    @Test
    fun emptyContainer_isWrongMagic() {
        assertFailsWith<BackupContainerException> { BackupContainer.decrypt(ByteArray(0), passphrase) }
    }

    private companion object {
        const val SAMPLE_JSON = """{"format":"wlo-backup","schemaVersion":1,"sections":{}}"""
    }
}
