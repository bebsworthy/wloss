package app.wlo.core.vault

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VerifiedReplacementWriterTest {
    @Test
    fun failedWrite_preservesPredecessorAndRemovesCandidate() =
        runTest {
            val backend = FakeBackend(oldBytes = byteArrayOf(1), failWrite = true)

            assertFailsWith<BackupStoreException> {
                VerifiedReplacementWriter(backend).replace(NAME, OLD_HANDLE, byteArrayOf(2))
            }

            assertContentEquals(byteArrayOf(1), backend.documents.getValue(OLD_HANDLE))
            assertEquals(setOf(OLD_HANDLE), backend.documents.keys)
        }

    @Test
    fun failedCreate_neverTouchesPredecessor() =
        runTest {
            val backend = FakeBackend(oldBytes = byteArrayOf(1), failCreate = true)

            assertFailsWith<BackupStoreException> {
                VerifiedReplacementWriter(backend).replace(NAME, OLD_HANDLE, byteArrayOf(2))
            }

            assertEquals(listOf("create"), backend.events)
            assertContentEquals(byteArrayOf(1), backend.documents.getValue(OLD_HANDLE))
        }

    @Test
    fun failedVerification_preservesPredecessorAndRemovesCandidate() =
        runTest {
            val backend = FakeBackend(oldBytes = byteArrayOf(1), corruptRead = true)

            assertFailsWith<BackupStoreException> {
                VerifiedReplacementWriter(backend).replace(NAME, OLD_HANDLE, byteArrayOf(2))
            }

            assertContentEquals(byteArrayOf(1), backend.documents.getValue(OLD_HANDLE))
            assertEquals(setOf(OLD_HANDLE), backend.documents.keys)
        }

    @Test
    fun predecessorIsDeletedOnlyAfterCandidateWasVerified() =
        runTest {
            val backend = FakeBackend(oldBytes = byteArrayOf(1))

            val result = VerifiedReplacementWriter(backend).replace(NAME, OLD_HANDLE, byteArrayOf(2))

            assertEquals(listOf("create", "write", "read", "delete-old", "rename"), backend.events)
            assertTrue(OLD_HANDLE !in backend.documents)
            assertContentEquals(byteArrayOf(2), backend.documents.getValue(result.handle))
            assertNotEquals(NAME, backend.createdName)
            assertTrue(RotationPolicy.isBackupFile(backend.createdName))
        }

    @Test
    fun failedPredecessorDelete_keepsVerifiedCandidateAsSafetyCopy() =
        runTest {
            val backend = FakeBackend(oldBytes = byteArrayOf(1), failDeleteOld = true)

            assertFailsWith<BackupStoreException> {
                VerifiedReplacementWriter(backend).replace(NAME, OLD_HANDLE, byteArrayOf(2))
            }

            assertContentEquals(byteArrayOf(1), backend.documents.getValue(OLD_HANDLE))
            assertContentEquals(byteArrayOf(2), backend.documents.getValue(NEW_HANDLE))
            assertEquals(listOf("create", "write", "read", "delete-old"), backend.events)
        }

    @Test
    fun unsupportedRename_keepsVerifiedProviderName() =
        runTest {
            val backend = FakeBackend(oldBytes = byteArrayOf(1), renameSupported = false)

            val result = VerifiedReplacementWriter(backend).replace(NAME, OLD_HANDLE, byteArrayOf(2))

            assertNotEquals(NAME, result.name)
            assertTrue(RotationPolicy.isBackupFile(result.name))
            assertContentEquals(byteArrayOf(2), backend.documents.getValue(result.handle))
        }

    @Test
    fun cancellationDuringWrite_isPropagatedAndPreservesPredecessor() =
        runTest {
            val backend = FakeBackend(oldBytes = byteArrayOf(1), cancelWrite = true)

            assertFailsWith<CancellationException> {
                VerifiedReplacementWriter(backend).replace(NAME, OLD_HANDLE, byteArrayOf(2))
            }

            assertEquals(setOf(OLD_HANDLE), backend.documents.keys)
        }

    @Test
    fun cancellationDuringRename_isPropagatedButKeepsVerifiedCandidate() =
        runTest {
            val backend = FakeBackend(oldBytes = byteArrayOf(1), cancelRename = true)

            assertFailsWith<CancellationException> {
                VerifiedReplacementWriter(backend).replace(NAME, OLD_HANDLE, byteArrayOf(2))
            }

            assertTrue(OLD_HANDLE !in backend.documents)
            assertContentEquals(byteArrayOf(2), backend.documents.getValue(NEW_HANDLE))
        }

    @Test
    fun providerReturningOriginalHandle_isRejectedBeforeWriting() =
        runTest {
            val backend = FakeBackend(oldBytes = byteArrayOf(1), reuseOldHandle = true)

            assertFailsWith<BackupStoreException> {
                VerifiedReplacementWriter(backend).replace(NAME, OLD_HANDLE, byteArrayOf(2))
            }

            assertEquals(listOf("create"), backend.events)
            assertContentEquals(byteArrayOf(1), backend.documents.getValue(OLD_HANDLE))
        }

    private class FakeBackend(
        oldBytes: ByteArray,
        private val failCreate: Boolean = false,
        private val failWrite: Boolean = false,
        private val corruptRead: Boolean = false,
        private val failDeleteOld: Boolean = false,
        private val renameSupported: Boolean = true,
        private val cancelWrite: Boolean = false,
        private val cancelRename: Boolean = false,
        private val reuseOldHandle: Boolean = false,
    ) : ReplacementBackend {
        val documents = linkedMapOf(OLD_HANDLE to oldBytes)
        val events = mutableListOf<String>()
        lateinit var createdName: String

        override suspend fun create(requestedName: String): ReplacementDocument {
            events += "create"
            if (failCreate) throw BackupStoreException("injected create failure")
            createdName = requestedName
            if (reuseOldHandle) return ReplacementDocument(OLD_HANDLE, requestedName)
            documents[NEW_HANDLE] = byteArrayOf()
            return ReplacementDocument(NEW_HANDLE, requestedName)
        }

        override suspend fun write(
            handle: String,
            bytes: ByteArray,
        ) {
            events += "write"
            if (cancelWrite) throw CancellationException("injected cancellation")
            if (failWrite) throw BackupStoreException("injected write failure")
            documents[handle] = bytes
        }

        override suspend fun read(handle: String): ByteArray {
            events += "read"
            return if (corruptRead) byteArrayOf(9) else documents.getValue(handle)
        }

        override suspend fun delete(handle: String): Boolean {
            events += if (handle == OLD_HANDLE) "delete-old" else "delete-new"
            if (handle == OLD_HANDLE && failDeleteOld) return false
            return documents.remove(handle) != null
        }

        override suspend fun rename(
            handle: String,
            requestedName: String,
        ): ReplacementDocument? {
            events += "rename"
            if (cancelRename) throw CancellationException("injected cancellation")
            return if (renameSupported) ReplacementDocument(handle, requestedName) else null
        }
    }

    private companion object {
        const val NAME = "wlo_backup_2026-09-15.wlo"
        const val OLD_HANDLE = "old"
        const val NEW_HANDLE = "new"
    }
}
