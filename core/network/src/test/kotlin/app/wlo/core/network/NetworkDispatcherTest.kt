package app.wlo.core.network

import app.wlo.core.consent.ConsentGate
import app.wlo.core.consent.ConsentTimeSource
import app.wlo.core.model.ConsentCapability
import app.wlo.core.ports.EgressDeniedException
import app.wlo.core.ports.EgressDownload
import app.wlo.core.ports.EgressPurpose
import app.wlo.core.ports.EgressRequest
import app.wlo.core.ports.OffLookupPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The fail-closed matrix over a real HTTP wire (localhost MockWebServer +
 * the production Ktor/OkHttp client): no grant → denied with a receipt and a
 * server that never saw a packet; grants run and receipt; R-C4 semantics —
 * default-on toggle, cache-first, per-lookup receipt rows; zoo downloads only
 * via the user-initiated ModelManager flow (R-S14); the expected-bytes cap
 * cuts oversized responses before they reach the sink.
 */
class NetworkDispatcherTest {
    private lateinit var server: MockWebServer
    private lateinit var dispatcher: NetworkDispatcher

    private val ledger = InMemoryEgressLedger()
    private val grants = mutableSetOf<ConsentCapability>()
    private var offToggleAllowed = true
    private val nowMs = 1_760_000_000_000L

    private val gate =
        object : ConsentGate {
            override suspend fun isGranted(capability: ConsentCapability): Boolean = capability in grants

            override suspend fun currentGrants(): Set<ConsentCapability> = grants
        }

    @Before
    public fun setUp() {
        server = MockWebServer()
        server.start()
        dispatcher =
            NetworkDispatcher(
                consentGate = gate,
                offLookupPolicy = OffLookupPolicy { offToggleAllowed },
                ledger = ledger,
                timeSource = ConsentTimeSource { nowMs },
            )
    }

    @After
    public fun tearDown() {
        dispatcher.close()
        server.close()
    }

    // --- the fail-closed matrix -------------------------------------------------

    @Test
    public fun futurePurposeWithoutGrant_isDenied_nothingSent_oneReceipt() =
        runTest {
            server.enqueue(MockResponse.Builder().body("leak").build())
            val result =
                dispatcher.dispatch(
                    EgressRequest<Unit>(
                        purpose = EgressPurpose.FUTURE_CLOUD_CHAT,
                        host = server.hostName,
                        operation = "chat/ask",
                        expectedBytes = 4,
                    ) { fetch(server.url("/v1/chat").toString()) },
                )
            assertTrue(result.isFailure)
            assertEquals(EgressPurpose.FUTURE_CLOUD_CHAT, (result.exceptionOrNull() as EgressDeniedException).purpose)
            assertEquals(0, server.requestCount, "denied requests must not open a socket")
            assertSingleReceipt(EgressOutcome.DENIED, bytes = 0)
            assertTrue(ReceiptChain.verify(ledger.recent(10)), "denials are receipted on the chain too")
        }

    @Test
    public fun futurePurposeWithGrant_runs_andReceiptsDeclaredBytes() =
        runTest {
            grants += ConsentCapability.INSIGHTS_CHAT
            server.enqueue(MockResponse.Builder().body("pong").build())
            val result =
                dispatcher.dispatch(
                    EgressRequest<String>(
                        purpose = EgressPurpose.FUTURE_CLOUD_CHAT,
                        host = server.hostName,
                        operation = "chat/ask",
                        expectedBytes = 4,
                    ) { fetch(server.url("/v1/chat").toString()) },
                )
            assertEquals("pong", result.getOrThrow())
            assertEquals(1, server.requestCount)
            assertSingleReceipt(EgressOutcome.OK, bytes = 4)
        }

    @Test
    public fun futurePurpose_granted_butBlankHost_isStillDenied() =
        runTest {
            grants += ConsentCapability.FOOD_PHOTO
            val result =
                dispatcher.dispatch(
                    EgressRequest<Unit>(
                        purpose = EgressPurpose.FUTURE_CLOUD_VISION,
                        host = "",
                        operation = "vision",
                    ) { error("never runs") },
                )
            assertTrue(result.exceptionOrNull() is EgressDeniedException)
            assertSingleReceipt(EgressOutcome.DENIED, bytes = 0)
        }

    @Test
    public fun eachFuturePurpose_mapsItsOwnCapability() =
        runTest {
            server.enqueue(MockResponse.Builder().body("x").build())
            // meal-planning grant does NOT unlock a voice-input call.
            grants += ConsentCapability.MEAL_PLANNING
            val result =
                dispatcher.dispatch(
                    EgressRequest<Unit>(
                        purpose = EgressPurpose.FUTURE_CLOUD_STT,
                        host = server.hostName,
                        operation = "stt",
                    ) { fetch(server.url("/stt").toString()) },
                )
            assertTrue(result.exceptionOrNull() is EgressDeniedException)
            assertEquals(0, server.requestCount, "cross-capability leakage must fail closed")
        }

    @Test
    public fun dispatch_cancellationIsReceiptedAndRethrown() =
        runTest {
            grants += ConsentCapability.INSIGHTS_CHAT

            assertFailsWith<CancellationException> {
                dispatcher.dispatch(
                    EgressRequest<Unit>(
                        purpose = EgressPurpose.FUTURE_CLOUD_CHAT,
                        host = server.hostName,
                        operation = "chat/cancelled",
                    ) { throw CancellationException("caller stopped") },
                )
            }

            assertSingleReceipt(EgressOutcome.FAILED, bytes = 0)
        }

    // --- R-C4: default on, cached, per-lookup audit trail -----------------------

    @Test
    public fun offLookup_ridesTheAllowance_withPerLookupReceipts() =
        runTest {
            server.enqueue(MockResponse.Builder().body("""{"status":1}""").build())
            val request: (String) -> EgressRequest<String> = { key ->
                EgressRequest<String>(
                    purpose = EgressPurpose.OFF_LOOKUP,
                    host = server.hostName,
                    operation = "off/product/3017620422003",
                    expectedBytes = 13,
                    cacheKey = key,
                ) { fetch(server.url("/api/v2/product/3017620422003").toString()) }
            }
            val first = dispatcher.dispatch(request("off:3017620422003"))
            assertEquals("{\"status\":1}", first.getOrThrow())
            val receipts = ledger.recent(10)
            assertEquals(1, receipts.size)
            assertEquals(EgressOutcome.OK, receipts.single().outcome)
            assertEquals(EgressPurpose.OFF_LOOKUP, receipts.single().purpose)
            assertEquals(1, server.requestCount)
        }

    @Test
    public fun offLookup_isCacheFirst_secondLookupSendsNothing() =
        runTest {
            server.enqueue(MockResponse.Builder().body("""{"status":1}""").build())
            val request =
                EgressRequest<String>(
                    purpose = EgressPurpose.OFF_LOOKUP,
                    host = server.hostName,
                    operation = "off/search",
                    expectedBytes = 13,
                    cacheKey = "off:search:oats",
                ) { fetch(server.url("/api/v2/search").toString()) }
            val first = dispatcher.dispatch(request)
            val second = dispatcher.dispatch(request)
            assertEquals(first.getOrThrow(), second.getOrThrow())
            assertEquals(1, server.requestCount, "cache-first: the second lookup must not hit the wire")
            val receipts = ledger.recent(10)
            assertEquals(2, receipts.size, "per-lookup audit trail: cached hits are receipted too")
            assertEquals(EgressOutcome.OK, receipts[0].outcome)
            assertEquals(EgressOutcome.CACHE_HIT, receipts[1].outcome)
            assertEquals(0, receipts[1].bytes, "cache hits send no bytes")
        }

    @Test
    public fun offLookup_toggleOff_failsClosed_perRc4() =
        runTest {
            offToggleAllowed = false
            val result =
                dispatcher.dispatch(
                    EgressRequest<Unit>(
                        purpose = EgressPurpose.OFF_LOOKUP,
                        host = server.hostName,
                        operation = "off/search",
                    ) { fetch(server.url("/api/v2/search").toString()) },
                )
            assertTrue(result.exceptionOrNull() is EgressDeniedException)
            assertEquals(0, server.requestCount)
            assertSingleReceipt(EgressOutcome.DENIED, bytes = 0)
        }

    // --- zoo downloads (R-S14) ---------------------------------------------------

    @Test
    public fun zooDownload_requiresTheUserInitiatedModelManagerFlow() =
        runTest {
            server.enqueue(MockResponse.Builder().body("model-bytes").build())
            val sink = Buffer()
            val result =
                dispatcher.download(
                    request =
                        EgressDownload(
                            "food-classifier/1",
                            server.url("/m.onnx").toString(),
                            11L,
                            userInitiated = false,
                        ),
                    sink = sink,
                )
            assertTrue(result.exceptionOrNull() is EgressDeniedException)
            assertEquals(0, server.requestCount)
            assertEquals(0, sink.size)
            assertSingleReceipt(EgressOutcome.DENIED, bytes = 0)
        }

    @Test
    public fun zooDownload_streamsCountsProgress_andReceiptsActualBytes() =
        runTest {
            val payload = "onnx-model-bytes-here"
            server.enqueue(MockResponse.Builder().body(payload).build())
            val sink = Buffer()
            val progress = mutableListOf<Long>()
            val result =
                dispatcher.download(
                    request =
                        EgressDownload(
                            modelId = "food-classifier/1",
                            url = server.url("/m.onnx").toString(),
                            expectedBytes = payload.length.toLong(),
                            userInitiated = true,
                            onProgress = { progress += it },
                        ),
                    sink = sink,
                )
            assertEquals(payload.length.toLong(), result.getOrThrow())
            assertEquals(payload, sink.readUtf8())
            assertTrue(progress.isNotEmpty(), "progress must be reported")
            assertEquals(payload.length.toLong(), progress.last())
            assertEquals(1, server.requestCount)
            assertSingleReceipt(EgressOutcome.OK, bytes = payload.length.toLong())
        }

    @Test
    public fun zooDownload_oversizedResponse_isCutBeforeTheSink() =
        runTest {
            val payload = "0123456789"
            server.enqueue(MockResponse.Builder().body(payload).build())
            val sink = Buffer()
            val result =
                dispatcher.download(
                    request =
                        EgressDownload("food-classifier/1", server.url("/m.onnx").toString(), 4L, userInitiated = true),
                    sink = sink,
                )
            assertTrue(result.isFailure, "declared 4 B but got ${payload.length} B — must be cut")
            assertEquals(0L, sink.size, "the oversized chunk must never reach the sink")
            assertSingleReceipt(EgressOutcome.FAILED, bytes = 0)
        }

    @Test
    public fun zooDownload_httpFailure_receiptsFailed() =
        runTest {
            server.enqueue(MockResponse.Builder().code(500).build())
            val result =
                dispatcher.download(
                    request =
                        EgressDownload(
                            "food-classifier/1",
                            server.url("/m.onnx").toString(),
                            10L,
                            userInitiated = true,
                        ),
                    sink = Buffer(),
                )
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals(1, server.requestCount)
            assertSingleReceipt(EgressOutcome.FAILED, bytes = 0)
        }

    @Test
    public fun zooDownload_malformedUrl_isDeniedWithoutARequest() =
        runTest {
            val result =
                dispatcher.download(
                    request = EgressDownload("food-classifier/1", "not a url", 10L, userInitiated = true),
                    sink = Buffer(),
                )
            assertTrue(result.exceptionOrNull() is EgressDeniedException)
            assertEquals(0, server.requestCount)
            assertSingleReceipt(EgressOutcome.DENIED, bytes = 0)
        }

    // --- assertions --------------------------------------------------------------

    private suspend fun assertSingleReceipt(
        outcome: EgressOutcome,
        bytes: Long,
    ) {
        val receipts = ledger.recent(10)
        assertEquals(1, receipts.size, "exactly one receipt per dispatch")
        val receipt = receipts.single()
        assertEquals(outcome, receipt.outcome)
        assertEquals(bytes, receipt.bytes)
        assertEquals(nowMs, receipt.atEpochMs)
    }

    /** Blocking fetch on the JVM test thread (the wire the block would use). */
    private fun fetch(url: String): String = URL(url).openStream().bufferedReader().use { it.readText() }
}
