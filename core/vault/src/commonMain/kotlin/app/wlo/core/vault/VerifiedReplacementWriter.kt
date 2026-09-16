package app.wlo.core.vault

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/** Opaque destination document used by the verified replacement state machine. */
internal data class ReplacementDocument(
    val handle: String,
    val name: String,
)

/** Minimal provider operations needed to replace one backup without deleting first. */
internal interface ReplacementBackend {
    suspend fun create(requestedName: String): ReplacementDocument

    suspend fun write(
        handle: String,
        bytes: ByteArray,
    )

    suspend fun read(handle: String): ByteArray

    suspend fun delete(handle: String): Boolean

    suspend fun rename(
        handle: String,
        requestedName: String,
    ): ReplacementDocument?
}

/**
 * Creates and verifies a replacement before retiring its predecessor. A
 * failed write/read leaves the known-good predecessor untouched. Once old-file
 * retirement begins, every failure keeps the verified candidate because a
 * provider can mutate successfully before reporting failure. A provider that
 * cannot rename simply keeps the listable temporary replacement.
 */
internal class VerifiedReplacementWriter(
    private val backend: ReplacementBackend,
    private val temporaryName: (String) -> String = ::uniqueTemporaryBackupName,
) {
    @Suppress("ThrowsCount", "TooGenericExceptionCaught") // Provider APIs expose only broad failures.
    suspend fun replace(
        requestedName: String,
        existingHandle: String?,
        bytes: ByteArray,
    ): ReplacementDocument {
        val candidate = backend.create(temporaryName(requestedName))
        if (candidate.handle == existingHandle) {
            throw BackupStoreException("destination did not create a distinct replacement for $requestedName")
        }
        var retirementStarted = false
        try {
            backend.write(candidate.handle, bytes)
            if (!backend.read(candidate.handle).contentEquals(bytes)) {
                throw BackupStoreException("destination could not verify the completed backup $requestedName")
            }
            if (existingHandle != null) {
                // From this point onward the provider may have deleted the old
                // document before reporting failure/cancellation. Keep the
                // verified candidate on every later failure so at least one
                // recoverable backup remains.
                retirementStarted = true
                if (!backend.delete(existingHandle)) {
                    throw BackupStoreException("destination could not retire the previous backup $requestedName")
                }
            }
        } catch (failure: Throwable) {
            if (!retirementStarted) {
                withContext(NonCancellable) {
                    runCatching { backend.delete(candidate.handle) }
                }
            }
            throw failure
        }

        return try {
            backend.rename(candidate.handle, requestedName) ?: candidate
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            candidate
        }
    }
}

/** A crash-left candidate remains visible to normal backup listing/rotation. */
private fun uniqueTemporaryBackupName(requestedName: String): String {
    val stem = requestedName.removeSuffix(RotationPolicy.FILE_SUFFIX)
    return "$stem.pending-${Uuid.random()}${RotationPolicy.FILE_SUFFIX}"
}
