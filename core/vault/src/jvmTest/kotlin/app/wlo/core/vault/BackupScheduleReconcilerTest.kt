package app.wlo.core.vault

import app.wlo.core.ports.BackupRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupScheduleReconcilerTest {
    @Test
    fun disablePersistsOffBeforeCancellingEnqueuedWork() =
        runTest {
            val events = mutableListOf<String>()
            val reconciler =
                BackupScheduleReconciler(
                    persistEnabled = { events += "policy-$it" },
                    enqueue = { events += "enqueue" },
                    cancel = { events += "cancel" },
                )

            reconciler.setEnabled(enabled = false, request = null)

            assertEquals(listOf("policy-false", "cancel"), events)
        }

    @Test
    fun cancelFailureStillLeavesPersistedPolicyOff() =
        runTest {
            val events = mutableListOf<String>()
            val reconciler =
                BackupScheduleReconciler(
                    persistEnabled = { events += "policy-$it" },
                    enqueue = { events += "enqueue" },
                    cancel = {
                        events += "cancel"
                        error("injected")
                    },
                )

            assertFailsWith<IllegalStateException> {
                reconciler.setEnabled(enabled = false, request = null)
            }

            assertEquals(listOf("policy-false", "cancel"), events)
        }

    @Test
    fun enqueueFailureRollsPolicyBackAndCancelsUncertainWork() =
        runTest {
            val events = mutableListOf<String>()
            val reconciler =
                BackupScheduleReconciler(
                    persistEnabled = { events += "policy-$it" },
                    enqueue = {
                        events += "enqueue"
                        error("injected")
                    },
                    cancel = { events += "cancel" },
                )

            assertFailsWith<IllegalStateException> {
                reconciler.setEnabled(enabled = true, request = REQUEST)
            }

            assertEquals(listOf("policy-true", "enqueue", "policy-false", "cancel"), events)
        }

    @Test
    fun enqueueCancellationIsPropagatedAfterFailClosedCleanup() =
        runTest {
            val events = mutableListOf<String>()
            val reconciler =
                BackupScheduleReconciler(
                    persistEnabled = { events += "policy-$it" },
                    enqueue = {
                        events += "enqueue"
                        throw CancellationException("stop")
                    },
                    cancel = { events += "cancel" },
                )

            assertFailsWith<CancellationException> {
                reconciler.setEnabled(enabled = true, request = REQUEST)
            }

            assertEquals(listOf("policy-true", "enqueue", "policy-false", "cancel"), events)
        }

    @Test
    fun enableWithoutDestinationDoesNotChangePolicyOrWork() =
        runTest {
            val events = mutableListOf<String>()
            val reconciler =
                BackupScheduleReconciler(
                    persistEnabled = { events += "policy-$it" },
                    enqueue = { events += "enqueue" },
                    cancel = { events += "cancel" },
                )

            assertFailsWith<IllegalArgumentException> {
                reconciler.setEnabled(enabled = true, request = null)
            }

            assertEquals(emptyList(), events)
        }

    private companion object {
        val REQUEST = BackupRequest(destinationUri = "content://backups", includeVault = false)
    }
}
