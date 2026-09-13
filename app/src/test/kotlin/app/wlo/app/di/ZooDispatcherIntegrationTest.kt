package app.wlo.app.di

import app.wlo.core.ai.OkioZooManager
import app.wlo.core.ai.ZooManifest
import app.wlo.core.ai.ZooModelState
import app.wlo.core.consent.ConsentTimeSource
import app.wlo.core.consent.InMemoryConsentLedger
import app.wlo.core.consent.ReplayConsentGate
import app.wlo.core.network.EgressLedger
import app.wlo.core.network.EgressOutcome
import app.wlo.core.network.InMemoryEgressLedger
import app.wlo.core.network.NetworkDispatcher
import app.wlo.core.ports.EgressPurpose
import app.wlo.core.ports.OffLookupPolicy
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole M4 PART A pipeline, production classes only, over a localhost
 * wire: NetworkDispatcher (enforcement + receipts) ← OkioZooManager
 * (download-once + hash-verify) ← InMemoryEgressLedger (the same chain
 * semantics as the Room ledger). Proves "download once — receipts prove it"
 * end to end: exactly ONE receipt exists after two ensureAvailable calls,
 * and a tampered payload lands as FAILED + CORRUPTED with nothing installed.
 */
class ZooDispatcherIntegrationTest {
    private val nowMs = 1_760_000_000_000L

    private fun dispatcher(ledger: EgressLedger): NetworkDispatcher =
        NetworkDispatcher(
            consentGate = ReplayConsentGate(InMemoryConsentLedger(ConsentTimeSource { nowMs })),
            offLookupPolicy = OffLookupPolicy { true },
            ledger = ledger,
            timeSource = ConsentTimeSource { nowMs },
        )

    private fun manifest(
        server: MockWebServer,
        sha256: String,
        size: Long,
    ): ZooManifest =
        ZooManifest.fromJson(
            """
            {
              "schemaVersion": 1,
              "revision": "integration/1",
              "models": [{
                "id": "food-classifier/1",
                "purpose": "food-photo",
                "url": "${server.url("/mobilenet_v2_food101.onnx")}",
                "sha256Hex": "$sha256",
                "sizeBytes": $size,
                "license": "Apache-2.0",
                "provenance": "Food-101 + ImageNet-init MobileNetV2, top-1 76.3%",
                "format": "onnx"
              }]
            }
            """.trimIndent(),
        )

    @Test
    fun downloadOnce_receiptsProveIt() =
        runBlocking<Unit> {
            val payload = "integration-payload-0123456789".encodeToByteArray()
            val sha = sha256Hex(payload)
            val server = MockWebServer()
            server.start()
            server.enqueue(MockResponse.Builder().body(okio.Buffer().write(payload)).build())

            val ledger = InMemoryEgressLedger()
            val zooDir = Files.createTempDirectory("wlo-zoo-int").toAbsolutePath()
            val zoo =
                OkioZooManager(
                    manifest = manifest(server, sha, payload.size.toLong()),
                    egress = dispatcher(ledger),
                    fileSystem = FileSystem.SYSTEM,
                    baseDir = zooDir.toString().toPath(),
                )

            val handle = zoo.ensureAvailable("food-classifier/1").getOrThrow()
            assertEquals(sha, handle.sha256Hex)
            assertEquals(payload.size.toLong(), handle.sizeBytes)
            zoo.ensureAvailable("food-classifier/1").getOrThrow()

            // Download-once, PROVEN by the receipt ledger: one trip, one receipt.
            assertEquals(1, server.requestCount)
            val zooReceipts = ledger.recent(10).filter { it.purpose == EgressPurpose.ZOO_DOWNLOAD }
            assertEquals(1, zooReceipts.size)
            val receipt = zooReceipts.single()
            assertEquals(EgressOutcome.OK, receipt.outcome)
            assertEquals(payload.size.toLong(), receipt.bytes)
            assertTrue(
                app.wlo.core.network.ReceiptChain
                    .verify(ledger.recent(10)),
            )

            server.close()
            zooDir.toFile().deleteRecursively()
        }

    @Test
    fun tamperedPayload_receiptsFailed_andNothingInstalls() =
        runBlocking<Unit> {
            val payload = "integration-payload-0123456789".encodeToByteArray()
            val server = MockWebServer()
            server.start()
            server.enqueue(
                MockResponse.Builder().body(okio.Buffer().write("TAMPERED-BYTES!!!".encodeToByteArray())).build(),
            )

            val ledger = InMemoryEgressLedger()
            val zooDir = Files.createTempDirectory("wlo-zoo-int").toAbsolutePath()
            val zoo =
                OkioZooManager(
                    manifest = manifest(server, sha256Hex(payload), payload.size.toLong()),
                    egress = dispatcher(ledger),
                    fileSystem = FileSystem.SYSTEM,
                    baseDir = zooDir.toString().toPath(),
                )

            assertTrue(zoo.ensureAvailable("food-classifier/1").isFailure)
            assertEquals(ZooModelState.Corrupted::class, zoo.state("food-classifier/1")::class)
            assertEquals(0, FileSystem.SYSTEM.list(zooDir.toString().toPath()).size, "nothing installed")

            // Receipt semantics: the dispatcher receipts the TRANSPORT (the
            // tampered bytes really did arrive, so "ok" + actual bytes); the
            // artifact verdict is the zoo manager's CORRUPTED state above.
            val receipt = ledger.recent(10).single()
            assertEquals(EgressOutcome.OK, receipt.outcome)
            assertEquals(EgressPurpose.ZOO_DOWNLOAD, receipt.purpose)
            assertEquals("TAMPERED-BYTES!!!".length.toLong(), receipt.bytes)

            server.close()
            zooDir.toFile().deleteRecursively()
        }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
