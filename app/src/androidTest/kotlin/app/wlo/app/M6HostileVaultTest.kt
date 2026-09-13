package app.wlo.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.wlo.core.database.WloDatabase
import app.wlo.core.vault.BackupContainer
import app.wlo.core.vault.BackupContainerException
import app.wlo.core.vault.BackupDocumentException
import app.wlo.core.vault.BackupKdf
import app.wlo.core.vault.BackupOptions
import app.wlo.core.vault.StagedRestorer
import app.wlo.core.vault.VaultFileStore
import app.wlo.core.vault.VaultKeys
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M6 instrumented hostiles + vault opacity, on the REAL device graph:
 *  - a truncated / wrong-magic / wrong-passphrase backup is REJECTED and the
 *    seeded data is untouched (F13 §4 flow 3: "nothing was changed");
 *  - a vault blob file is CIPHERTEXT at an app-private path, never
 *    registered with MediaStore (F13 §9 invariant, T-C3/T-G3).
 */
@RunWith(AndroidJUnit4::class)
public class M6HostileVaultTest {
    @Test
    public fun hostileBackups_areRejected_andDataIsUntouched() {
        val koin = GlobalContext.get()
        val db = koin.get<WloDatabase>()
        val restorer = koin.get<StagedRestorer>()

        M6E2eSpec.seed()
        val goodBytes =
            runBlocking {
                // Build one REAL backup via the pipeline into app-private SAF
                // stand-in: the manager needs a destination uri; the app's own
                // external files dir tree works through SafBackupStore only
                // with a granted tree — so use the in-memory path instead:
                // assemble + encrypt directly (the same code backupNow runs).
                val assembler = koin.get<app.wlo.core.vault.SnapshotAssembler>()
                val payload = assembler.assemble(BackupOptions())
                val json =
                    app.wlo.core.vault.BackupCodec
                        .encode(payload, 1_760_000_000_000)
                val salt = ByteArray(app.wlo.core.vault.BackupKdf.SALT_BYTES)
                app.wlo.core.vault.BackupContainer.encrypt(
                    json,
                    app.wlo.core.vault.BackupKdf
                        .argon2idDefaults(salt),
                    M6E2eSpec.PASSPHRASE.toCharArray(),
                )
            }
        val before = counts(db)

        // 1. Truncated container.
        assertFailsWith<BackupContainerException> {
            runBlocking { restorer.stage(goodBytes.copyOfRange(0, goodBytes.size / 2), M6E2eSpec.PASSPHRASE.toCharArray()) }
        }
        // 2. Wrong magic.
        assertFailsWith<BackupContainerException> {
            runBlocking { restorer.stage("definitely not a wlo backup".toByteArray(), M6E2eSpec.PASSPHRASE.toCharArray()) }
        }
        // 3. Wrong passphrase (GCM tag failure — the clean-error contract).
        val wrongTag =
            assertFailsWith<BackupContainerException> {
                runBlocking { restorer.stage(goodBytes, "wrong-passphrase".toCharArray()) }
            }
        assertEquals(BackupContainerException.Reason.WRONG_PASSPHRASE, wrongTag.reason)
        // 4. Manifest-tampered: valid container, altered section bytes.
        val decrypted = BackupContainer.decrypt(goodBytes, M6E2eSpec.PASSPHRASE.toCharArray())
        val tamperedText =
            decrypted.plaintextJson
                .toString(Charsets.UTF_8)
                .replaceFirst("\"birthYear\":${M6E2eSpec.BIRTH_YEAR}", "\"birthYear\":1")
        val resealed =
            BackupContainer.encrypt(
                tamperedText.toByteArray(),
                BackupKdf.argon2idDefaults(decrypted.kdfParams.salt),
                M6E2eSpec.PASSPHRASE.toCharArray(),
            )
        val tamper =
            assertFailsWith<BackupDocumentException> {
                runBlocking { restorer.stage(resealed, M6E2eSpec.PASSPHRASE.toCharArray()) }
            }
        assertEquals(BackupDocumentException.Reason.MANIFEST_MISMATCH, tamper.reason)

        // Nothing was changed — byte-for-byte count equality (F13 §4 flow 3).
        assertEquals(before, counts(db))
        println("M6E2E hostile: all hostile backups rejected; data untouched ($before)")
    }

    @Test
    public fun vaultBlob_isCiphertext_appPrivate_neverInMediaStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = VaultFileStore(context)
        val plaintext = ByteArray(4096) { (it % 251).toByte() } // a "photo"

        val id = store.put(VaultKeys.Partition.PHOTO, plaintext, logicalName = "meal-42.jpg", contentType = "image/jpeg", atEpochMs = 1L)

        val dir = store.partitionDir(VaultKeys.Partition.PHOTO)
        val blobFile = File(dir, "$id.blob")
        assertTrue(blobFile.exists(), "blob at app-private path")
        assertTrue(blobFile.path.contains("/files/vault/photo/"), "path is app-private: ${blobFile.path}")
        assertTrue(File(dir, ".nomedia").exists(), "the media-scanner anchor exists")

        // Opacity: the file at that path is NOT the plaintext — it is IV+ciphertext.
        val stored = blobFile.readBytes()
        assertTrue(stored.size > plaintext.size, "ciphertext carries IV + tag")
        assertFalse(stored.contentEquals(plaintext), "stored bytes are ciphertext")
        // And it decrypts back, via the partition's Keystore key.
        val key = VaultKeys.partitionKey(VaultKeys.Partition.PHOTO.keyAlias)
        assertTrue(
            app.wlo.core.vault.VaultCipher
                .decrypt(key, stored)
                .contentEquals(plaintext),
            "round-trip through the partition key",
        )

        // MediaStore never learned about it: no media provider row for the path.
        val resolver = context.contentResolver
        val query =
            resolver.query(
                android.provider.MediaStore.Files
                    .getContentUri("external"),
                arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                "${android.provider.MediaStore.MediaColumns.DATA} LIKE ?",
                arrayOf("%vault/photo%"),
                null,
            )
        query?.use { cursor -> assertEquals(0, cursor.count, "vault blobs are invisible to MediaStore") }

        // Storage accounting sees it (F13 §3 storage dashboard data API).
        val usage = store.accounting(VaultKeys.Partition.PHOTO)
        assertTrue(usage.bytes >= stored.size && usage.count >= 1)
    }

    private data class Counts(
        val rows: Int,
    )

    private fun counts(db: WloDatabase): Counts =
        runBlocking {
            Counts(
                db.profiles().all().size +
                    db.measurementEvents().all().size +
                    db.diaryEntries().all().size +
                    db.diaryEntryRevisions().all().size +
                    db.foodItems().countAll() +
                    db.recipes().all().size +
                    db.listItems().all().size +
                    db.pantryItems().all().size +
                    db.targetsVersions().all().size +
                    db.consentLedger().all().size,
            )
        }
}
