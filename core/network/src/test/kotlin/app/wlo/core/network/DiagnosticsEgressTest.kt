package app.wlo.core.network

import app.wlo.core.consent.ConsentGate
import app.wlo.core.consent.ConsentTimeSource
import app.wlo.core.ports.DiagnosticsPolicy
import app.wlo.core.ports.EgressDeniedException
import app.wlo.core.ports.EgressPurpose
import app.wlo.core.ports.EgressRequest
import app.wlo.core.ports.OffLookupPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-K4 (q-000026): DIAGNOSTICS egress rides the explicit settings toggle,
 * default OFF, never a consent capability. Plus the AcraReportSender
 * content-freeing pipeline: framework frames redact, app frames survive,
 * every other report field is dropped.
 */
class DiagnosticsEgressTest {
    private val ledger = InMemoryEgressLedger()
    private var toggleOn = false

    private val gate =
        object : ConsentGate {
            override suspend fun isGranted(capability: app.wlo.core.model.ConsentCapability) = false

            override suspend fun currentGrants() = emptySet<app.wlo.core.model.ConsentCapability>()
        }

    private fun dispatcher(): NetworkDispatcher =
        NetworkDispatcher(
            consentGate = gate,
            offLookupPolicy = OffLookupPolicy { true },
            ledger = ledger,
            timeSource = ConsentTimeSource { 0L },
            diagnosticsPolicy = DiagnosticsPolicy { toggleOn },
        )

    @Test
    fun diagnostics_toggleOff_deniesAndReceiptsZero() =
        runTest {
            val result =
                dispatcher().dispatch(
                    EgressRequest<Unit>(
                        purpose = EgressPurpose.DIAGNOSTICS,
                        host = "crashes.example.net",
                        operation = "acra/crash-report",
                        expectedBytes = 100,
                    ) { },
                )
            assertTrue(result.isFailure)
            val denial = result.exceptionOrNull() as EgressDeniedException
            assertEquals(EgressPurpose.DIAGNOSTICS, denial.purpose)
            assertTrue("toggle is off" in denial.reason)
            val receipt = ledger.recent(1).single()
            assertEquals(EgressOutcome.DENIED, receipt.outcome)
            assertEquals(0, receipt.bytes)
        }

    @Test
    fun diagnostics_toggleOn_withoutGrant_stillServes_consentTaxonomyUntouched() =
        runTest {
            // The six-category matrix is irrelevant here: the settings toggle
            // alone serves this purpose (T-K4 is NOT an F12 capability, R-C1).
            toggleOn = true
            val result =
                dispatcher().dispatch(
                    EgressRequest<Unit>(
                        purpose = EgressPurpose.DIAGNOSTICS,
                        host = "crashes.example.net",
                        operation = "acra/crash-report",
                        expectedBytes = 100,
                    ) { },
                )
            assertTrue(result.isSuccess)
            val receipt = ledger.recent(1).single()
            assertEquals(EgressOutcome.OK, receipt.outcome)
        }

    @Test
    fun acraScrub_keepsAppFrames_redactsFramework_dropsFields() {
        val raw =
            """
            APP_VERSION_CODE=7
            ANDROID_VERSION=37
            PHONE_MODEL=emulator64
            USER_EMAIL=me@home.example
            USER_COMMENT=ate too many lentils, weight 84.2 kg
            STACK_TRACE=java.lang.IllegalStateException: held
              at app.wlo.core.data.RoomDiaryRepository.edit(DiaryRepository.kt:88)
              at androidx.fragment.app.FragmentManager.dispatch(FragmentManager.java:3110)
              at java.lang.reflect.Method.invoke(Native Method)
            LOGCAT=09-12 06:59:59 I/Art: user typed a note about their body
            """.trimIndent()
        val sender =
            AcraReportSender(
                egress = NoEgress,
                endpointUrl = "",
            )
        val scrubbed = sender.scrub(raw)
        assertTrue("app.wlo.core.data.RoomDiaryRepository.edit" in scrubbed, "app frames survive")
        assertTrue("framework frame redacted" in scrubbed)
        assertTrue("androidx.fragment" !in scrubbed)
        assertTrue("java.lang.reflect" !in scrubbed)
        assertTrue("me@home.example" !in scrubbed, "user fields dropped")
        assertTrue("lentils" !in scrubbed, "user content dropped")
        assertTrue("LOGCAT" !in scrubbed)
        assertTrue("84.2" !in scrubbed)
    }

    @Test
    fun acraSend_blankEndpoint_failsClosed() =
        runTest {
            val sender =
                AcraReportSender(
                    egress = dispatcher(),
                    endpointUrl = "",
                )
            val result = sender.send("APP_VERSION_CODE=7")
            assertTrue(result.isFailure)
        }

    private object NoEgress : app.wlo.core.ports.EgressPort {
        override suspend fun <R : Any> dispatch(request: app.wlo.core.ports.EgressRequest<R>): Result<R> =
            Result.failure(IllegalStateException("not used"))

        override suspend fun download(
            request: app.wlo.core.ports.EgressDownload,
            sink: okio.Sink,
        ): Result<Long> = Result.failure(IllegalStateException("not used"))
    }
}
