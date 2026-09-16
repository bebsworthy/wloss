package app.wlo.core.vault

import app.wlo.core.ports.BackupRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Atomic-enough policy/scheduler reconciliation around platform work APIs. */
internal class BackupScheduleReconciler(
    private val persistEnabled: suspend (Boolean) -> Unit,
    private val enqueue: suspend (BackupRequest) -> Unit,
    private val cancel: suspend () -> Unit,
) {
    @Suppress("TooGenericExceptionCaught") // Platform enqueue APIs expose broad failures.
    suspend fun setEnabled(
        enabled: Boolean,
        request: BackupRequest?,
    ) {
        if (!enabled) {
            // Persist the fail-closed gate first: even work already dispatched
            // by the scheduler will observe OFF before attempting a write.
            persistEnabled(false)
            cancel()
            return
        }

        val activeRequest =
            requireNotNull(request) {
                "A backup destination is required when enabling automatic backup"
            }
        persistEnabled(true)
        try {
            enqueue(activeRequest)
        } catch (cancelled: CancellationException) {
            rollbackFailedEnable()
            throw cancelled
        } catch (failure: Exception) {
            rollbackFailedEnable()
            throw failure
        }
    }

    private suspend fun rollbackFailedEnable() {
        // Enqueue is not transactional with DataStore. Roll policy back and
        // cancel uncertain work even when the caller itself was cancelled.
        withContext(NonCancellable) {
            persistEnabled(false)
            runCatching { cancel() }
        }
    }
}

/** Pure policy seam for BackupWorker's persisted-policy gate and retry behavior. */
internal class ScheduledBackupRunner(
    private val isEnabled: suspend () -> Boolean,
    private val backup: suspend () -> Unit,
) {
    suspend fun run(
        runAttemptCount: Int,
        maxAttempts: Int,
    ): ScheduledBackupResult =
        try {
            if (isEnabled()) backup()
            ScheduledBackupResult.SUCCESS
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount + 1 >= maxAttempts) {
                ScheduledBackupResult.FAILURE
            } else {
                ScheduledBackupResult.RETRY
            }
        }
}

internal enum class ScheduledBackupResult {
    SUCCESS,
    RETRY,
    FAILURE,
}
