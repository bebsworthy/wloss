package app.wlo.core.vault

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScheduledBackupRunnerTest {
    @Test
    fun disabledPolicy_skipsAlreadyEnqueuedBackup() =
        runTest {
            var writes = 0
            val runner = ScheduledBackupRunner(isEnabled = { false }, backup = { writes += 1 })

            assertEquals(ScheduledBackupResult.SUCCESS, runner.run(runAttemptCount = 0, maxAttempts = 3))
            assertEquals(0, writes)
        }

    @Test
    fun enabledPolicy_runsBackup() =
        runTest {
            var writes = 0
            val runner = ScheduledBackupRunner(isEnabled = { true }, backup = { writes += 1 })

            assertEquals(ScheduledBackupResult.SUCCESS, runner.run(runAttemptCount = 0, maxAttempts = 3))
            assertEquals(1, writes)
        }

    @Test
    fun failureRetriesUntilFinalConfiguredAttempt() =
        runTest {
            val runner = ScheduledBackupRunner(isEnabled = { true }, backup = { error("injected") })

            assertEquals(ScheduledBackupResult.RETRY, runner.run(runAttemptCount = 0, maxAttempts = 3))
            assertEquals(ScheduledBackupResult.RETRY, runner.run(runAttemptCount = 1, maxAttempts = 3))
            assertEquals(ScheduledBackupResult.FAILURE, runner.run(runAttemptCount = 2, maxAttempts = 3))
        }

    @Test
    fun policyReadFailureFailsClosedWithoutWriting() =
        runTest {
            var writes = 0
            val runner = ScheduledBackupRunner(isEnabled = { error("injected") }, backup = { writes += 1 })

            assertEquals(ScheduledBackupResult.RETRY, runner.run(runAttemptCount = 0, maxAttempts = 3))
            assertEquals(0, writes)
        }

    @Test
    fun cancellationIsNeverConvertedToRetry() =
        runTest {
            val runner =
                ScheduledBackupRunner(
                    isEnabled = { true },
                    backup = { throw CancellationException("stop") },
                )

            assertFailsWith<CancellationException> {
                runner.run(runAttemptCount = 0, maxAttempts = 3)
            }
        }
}
