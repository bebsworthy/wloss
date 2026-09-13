package app.wlo.core.ai

import app.wlo.core.ports.EgressDownload
import app.wlo.core.ports.EgressPort
import app.wlo.core.ports.EgressRequest
import app.wlo.core.ports.ModelHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The zoo state machine over a real localhost HTTP wire (MockWebServer) with
 * a thin JDK-socket [EgressPort] stand-in for the bound NetworkDispatcher:
 * size disclosure BEFORE any download, progress, hash-mismatch → CORRUPTED
 * with nothing installed, re-download heals, reclaim, download-once (the
 * fake port counts invocations like the receipt ledger would prove them).
 */
class ZooManagerTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var server: MockWebServer
    private lateinit var baseDir: String
    private var downloads = 0

    /** MockWebServer per test (wire truth) + a scratch zoo directory. */
    private fun setUp() {
        server = MockWebServer()
        server.start()
        baseDir =
            java.nio.file.Files
                .createTempDirectory("wlo-zoo-test")
                .toAbsolutePath()
                .toString()
        downloads = 0
    }

    private fun tearDown() {
        server.close()
    }

    private val payload: ByteArray by lazy { "onnx-artifact-payload-0123456789".encodeToByteArray() }
    private val payloadSha256: String by lazy { sha256Hex(payload) }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private fun newManager(manifest: ZooManifest): OkioZooManager =
        OkioZooManager(
            manifest = manifest,
            egress = FakeEgressPort { downloads++ },
            fileSystem = fileSystem,
            baseDir = baseDir.toPath(),
        )

    private fun manifest(): ZooManifest =
        ZooManifest.fromJson(
            """
            {
              "schemaVersion": 1,
              "revision": "test-zoo/1",
              "models": [{
                "id": "food-classifier/1",
                "purpose": "food-photo",
                "url": "${server.url("/mobilenet_v2_food101.onnx")}",
                "sha256Hex": "$payloadSha256",
                "sizeBytes": ${payload.size},
                "license": "Apache-2.0",
                "provenance": "Food-101 + ImageNet-init MobileNetV2, top-1 76.3%",
                "format": "onnx"
              }]
            }
            """.trimIndent(),
        )

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun catalogDisclosesSizeAndHash_beforeAnythingDownloads() =
        runBlocking(Dispatchers.IO) {
            setUp()
            val zoo = newManager(manifest())
            val model = zoo.catalog().single()
            assertEquals("food-classifier/1", model.id)
            assertEquals(payload.size.toLong(), model.sizeBytes, "the UI shows THIS size before download")
            assertEquals(payloadSha256, model.sha256Hex)
            assertEquals("Apache-2.0", model.license)
            assertEquals(ZooModelState.NotDownloaded, zoo.state("food-classifier/1"))
            assertEquals(0, server.requestCount, "disclosure must be offline (bundled manifest)")
            assertEquals(0, zoo.storageBytes())
            tearDown()
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun downloadProgressesVerifiesAndInstallsAtomically() =
        runBlocking(Dispatchers.IO) {
            setUp()
            server.enqueue(body(payload))
            val zoo = newManager(manifest())
            val handle = zoo.ensureAvailable("food-classifier/1").getOrThrow()
            // The PART-B handle carries the artifact path (analyzer loads from it).
            assertEquals(
                ModelHandle(
                    modelId = "food-classifier/1",
                    sizeBytes = payload.size.toLong(),
                    sha256Hex = payloadSha256,
                    filePath = baseDir + "/food-classifier_1.onnx",
                ),
                handle,
            )
            val state = zoo.state("food-classifier/1") as ZooModelState.DownloadedVerified
            assertEquals(payload.size.toLong(), state.sizeBytes)
            assertEquals(payloadSha256, state.sha256Hex)
            assertTrue(state.installedAtEpochMs > 0, "install time comes from the file mtime")
            assertEquals(1, downloads, "exactly one egress trip")
            assertEquals(1, server.requestCount)
            assertEquals(payload.size.toLong(), zoo.storageBytes())
            assertTrue(
                fileSystem.list(baseDir.toPath()).none { it.name.endsWith(".part") },
                "the temp file must be gone after the atomic move",
            )
            tearDown()
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun downloadOnce_verifiedArtifactsNeverReDownload() =
        runBlocking(Dispatchers.IO) {
            setUp()
            server.enqueue(body(payload))
            val zoo = newManager(manifest())
            val first = zoo.ensureAvailable("food-classifier/1").getOrThrow()
            val second = zoo.ensureAvailable("food-classifier/1").getOrThrow()
            assertEquals(first, second)
            assertEquals(1, downloads, "download-once: the second call must not touch the wire")
            assertEquals(1, server.requestCount)
            tearDown()
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun hashMismatch_corrupted_nothingInstalled() =
        runBlocking(Dispatchers.IO) {
            setUp()
            val wrong = "X".repeat(payload.size).encodeToByteArray()
            assertEquals(payload.size, wrong.size, "the size check alone must not pass this test")
            server.enqueue(body(wrong))
            val zoo = newManager(manifest())
            val result = zoo.ensureAvailable("food-classifier/1")
            assertTrue(result.isFailure, "a hash mismatch must fail the download")
            val state = zoo.state("food-classifier/1")
            assertTrue(state is ZooModelState.Corrupted, "saw $state")
            assertEquals(0, zoo.storageBytes(), "quarantined: nothing (not even a .part) stays on disk")
            assertEquals(1, downloads)
            tearDown()
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun redownloadHealsCorrupted() =
        runBlocking(Dispatchers.IO) {
            setUp()
            server.enqueue(body("bad-bytes".encodeToByteArray()))
            val zoo = newManager(manifest())
            assertTrue(zoo.ensureAvailable("food-classifier/1").isFailure)
            server.enqueue(body(payload))
            val healed = zoo.ensureAvailable("food-classifier/1")
            assertTrue(healed.isSuccess, "re-download is how CORRUPTED heals")
            assertEquals(
                ZooModelState.DownloadedVerified::class,
                zoo.state("food-classifier/1")::class,
            )
            assertEquals(2, downloads)
            tearDown()
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun transportFailure_isNotCorruption() =
        runBlocking(Dispatchers.IO) {
            setUp()
            server.enqueue(MockResponse.Builder().code(404).build())
            val zoo = newManager(manifest())
            assertTrue(zoo.ensureAvailable("food-classifier/1").isFailure)
            assertEquals(
                ZooModelState.NotDownloaded,
                zoo.state("food-classifier/1"),
                "offline/404 is retry-later, not a corrupted artifact",
            )
            tearDown()
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun reclaimResetsStateAndStorage_receiptsStay() =
        runBlocking(Dispatchers.IO) {
            setUp()
            server.enqueue(body(payload))
            val zoo = newManager(manifest())
            zoo.ensureAvailable("food-classifier/1").getOrThrow()
            assertEquals(1, downloads, "receipt evidence of the one download")
            assertTrue(zoo.reclaim("food-classifier/1").isSuccess)
            assertEquals(ZooModelState.NotDownloaded, zoo.state("food-classifier/1"))
            assertEquals(0, zoo.storageBytes())
            assertEquals(1, downloads, "reclaim does not rewrite history: receipts are append-only")
            // Re-download after reclaim works (first-use semantics again).
            server.enqueue(body(payload))
            assertTrue(zoo.ensureAvailable("food-classifier/1").isSuccess)
            assertEquals(2, downloads)
            tearDown()
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun startupVerifiesDiskTruth() =
        runBlocking(Dispatchers.IO) {
            setUp()
            server.enqueue(body(payload))
            val first = newManager(manifest())
            first.ensureAvailable("food-classifier/1").getOrThrow()

            // A fresh manager over the same storage sees the verified artifact —
            // airplane-mode parity: no network needed to "ensure" it.
            val restarted = newManager(manifest())
            val before = server.requestCount
            assertTrue(restarted.ensureAvailable("food-classifier/1").isSuccess)
            assertEquals(before, server.requestCount, "verified-on-disk must not re-download")

            // Tampering on disk is detected at startup as CORRUPTED.
            val target = fileSystem.list(baseDir.toPath()).single { it.name.endsWith(".onnx") }
            fileSystem.write(target) { write("X".encodeToByteArray()) }
            val tampered = newManager(manifest())
            val state = tampered.state("food-classifier/1")
            assertTrue(state is ZooModelState.Corrupted, "saw $state")
            tearDown()
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun unknownModelFails_withoutAnyEgress() =
        runBlocking(Dispatchers.IO) {
            setUp()
            val zoo = newManager(manifest())
            assertTrue(zoo.ensureAvailable("nope/9").isFailure)
            assertTrue(zoo.reclaim("nope/9").isFailure)
            assertEquals(0, downloads)
            tearDown()
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun manifestHouseRules_unknownKeysTolerated_newerSchemaRefused() {
        val json =
            """
            {
              "schemaVersion": 1,
              "futureField": true,
              "models": [{
                "id": "x/1", "purpose": "food-photo", "url": "https://example.invalid/x.onnx",
                "sha256Hex": "${"a".repeat(64)}", "sizeBytes": 1, "license": "MIT",
                "provenance": "none", "format": "onnx", "someFutureKey": 7
              }]
            }
            """.trimIndent()
        val parsed = ZooManifest.fromJson(json)
        assertEquals("x/1", parsed.models.single().id, "unknown keys must be tolerated (ADR-004 rule 2)")
        val newer = json.replace("\"schemaVersion\": 1,", "\"schemaVersion\": 99,")
        kotlin.test.assertFailsWith<IllegalStateException> { ZooManifest.fromJson(newer) }
    }

    // --- plumbing ---------------------------------------------------------------

    private fun body(bytes: ByteArray): MockResponse = MockResponse.Builder().body(okio.Buffer().write(bytes)).build()

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** Minimal JDK-socket EgressPort stand-in for the bound NetworkDispatcher. */
    private inner class FakeEgressPort(
        private val onTrip: () -> Unit,
    ) : EgressPort {
        override suspend fun <R : Any> dispatch(request: EgressRequest<R>): Result<R> =
            Result.failure(UnsupportedOperationException("dispatch is not part of the zoo flow"))

        override suspend fun download(
            request: EgressDownload,
            sink: okio.Sink,
        ): Result<Long> {
            onTrip()
            return try {
                val connection = URL(request.url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                require(request.userInitiated) { "the dispatcher enforces userInitiated" }
                var written = 0L
                connection.inputStream.use { input ->
                    sink.use { out ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (written + read > request.expectedBytes) {
                                throw IOException("oversized response — cut")
                            }
                            written += read
                            // Dispatcher-style raw write: the HashingSink sees
                            // every byte without an intermediate buffer.
                            out.write(okio.Buffer().apply { write(buffer, 0, read) }, read.toLong())
                            request.onProgress(written)
                        }
                    }
                }
                Result.success(written)
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
    }
}
