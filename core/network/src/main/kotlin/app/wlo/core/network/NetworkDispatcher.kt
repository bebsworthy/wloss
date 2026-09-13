package app.wlo.core.network

import app.wlo.core.consent.ConsentGate
import app.wlo.core.consent.ConsentTimeSource
import app.wlo.core.ports.EgressDeniedException
import app.wlo.core.ports.EgressDownload
import app.wlo.core.ports.EgressPort
import app.wlo.core.ports.EgressPurpose
import app.wlo.core.ports.EgressRequest
import app.wlo.core.ports.OffLookupPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.Sink
import java.io.IOException
import java.net.URI

/**
 * F12 §3.8's single socket door — the ONLY code in the tree that may open one.
 * Every dispatch and every streamed download:
 *
 *  1. passes enforcement by purpose (fail-closed by default):
 *     - [EgressPurpose.ZOO_DOWNLOAD] — R-S14: served only when the request is
 *       user-initiated (the ModelManager flow that showed the size first);
 *     - [EgressPurpose.OFF_LOOKUP] — R-C4 verbatim: "F13 integration toggle
 *       (not an F12 AI category), default on, cached, per-lookup audit trail"
 *       → served cache-first while [OffLookupPolicy] allows, one receipt row
 *       per lookup;
 *     - every `FUTURE_*` purpose — served only while its
 *       [app.wlo.core.model.ConsentCapability] holds an active grant, consumed
 *       through the injected :core:consent [ConsentGate] (dependency inversion:
 *       this module depends on the abstraction, never the reverse);
 *  2. writes exactly one hash-chained receipt — success, failure AND denial
 *     (denials prove the negative: bytes = 0, the call never ran).
 *
 * Receipt `bytes` semantics: actual bytes for downloads (measured while the
 * body streams, capped at the declared expectation); the DECLARED
 * [EgressRequest.expectedBytes] for opaque dispatch blocks (the caller's
 * attestation of what would leave); always 0 for denials and cache hits.
 *
 * Timeouts per ADR-004: the whole-call request timeout is null (SSE/LLM
 * streams must never be killed mid-stream); an inactivity guard
 * ([SOCKET_INACTIVITY_MS]) plus a connect timeout cover dead sockets.
 *
 * Redirects: the OkHttp engine follows HTTPS redirects (the zoo's upstream —
 * Hugging Face — serves payloads from its CDN). Receipts name the pinned host
 * the caller declared; content authenticity does not depend on the hop,
 * because zoo payloads are sha256-verified before install (R-S14).
 */
public class NetworkDispatcher(
    private val consentGate: ConsentGate,
    private val offLookupPolicy: OffLookupPolicy,
    private val ledger: EgressLedger,
    private val timeSource: ConsentTimeSource,
    /** Injectable for tests; production wiring uses [defaultEgressClient]. */
    private val httpClient: HttpClient? = null,
    cacheMaxEntries: Int = DEFAULT_CACHE_ENTRIES,
) : EgressPort,
    AutoCloseable {
    private val client: HttpClient by lazy { httpClient ?: defaultEgressClient() }

    /** R-C4 "cached": the cache-first store behind OFF lookups (in-memory LRU). */
    private val cacheLock = Any()
    private val cache = ResponseLruCache(cacheMaxEntries)

    override suspend fun <R : Any> dispatch(request: EgressRequest<R>): Result<R> {
        denialFor(request.purpose, request.userInitiated, request.host)?.let { reason ->
            writeReceipt(request.purpose, request.host, request.operation, bytes = 0, EgressOutcome.DENIED)
            return Result.failure(EgressDeniedException(request.purpose, reason))
        }

        val cacheKey = request.cacheKey
        if (cacheKey != null) {
            val cached: Any? = synchronized(cacheLock) { cache[cacheKey] }
            if (cached != null) {
                @Suppress("UNCHECKED_CAST")
                val hit = cached as R
                writeReceipt(request.purpose, request.host, request.operation, bytes = 0, EgressOutcome.CACHE_HIT)
                return Result.success(hit)
            }
        }

        val result = runCatching { request.block() }
        writeReceipt(
            purpose = request.purpose,
            host = request.host,
            operation = request.operation,
            bytes = request.expectedBytes ?: 0L,
            outcome = if (result.isSuccess) EgressOutcome.OK else EgressOutcome.FAILED,
        )
        if (result.isSuccess && cacheKey != null) {
            synchronized(cacheLock) { cache[cacheKey] = result.getOrThrow() }
        }
        return result
    }

    override suspend fun download(
        request: EgressDownload,
        sink: Sink,
    ): Result<Long> {
        val purpose = EgressPurpose.ZOO_DOWNLOAD
        val operation = "zoo/${request.modelId}"
        val host = hostOf(request.url)
        if (host == null) {
            writeReceipt(purpose, request.url, operation, bytes = 0, EgressOutcome.DENIED)
            return Result.failure(EgressDeniedException(purpose, "malformed zoo URL"))
        }
        if (!request.userInitiated) {
            writeReceipt(purpose, host, operation, bytes = 0, EgressOutcome.DENIED)
            return Result.failure(
                EgressDeniedException(purpose, "zoo downloads require the user-initiated ModelManager flow (R-S14)"),
            )
        }

        var written = 0L
        try {
            client.prepareGet(request.url).execute { response ->
                if (!response.status.isSuccess()) {
                    throw IOException("zoo download HTTP ${response.status.value}")
                }
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(DOWNLOAD_CHUNK_BYTES)
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (written + read > request.expectedBytes) {
                        // Cut BEFORE the oversized chunk reaches the sink: the
                        // receipt's bytes stay what actually was delivered.
                        throw IOException(
                            "zoo response exceeds declared size (${request.expectedBytes} B) — cut",
                        )
                    }
                    written += read
                    // Raw-Sink write: a HashingSink handed in by the zoo manager
                    // sees every byte in the same pass (no intermediate buffer).
                    sink.write(Buffer().apply { write(buffer, 0, read) }, read.toLong())
                    request.onProgress(written)
                }
            }
            writeReceipt(purpose, host, operation, written, EgressOutcome.OK)
            return Result.success(written)
        } catch (cancelled: CancellationException) {
            // Caller cancelled mid-stream (e.g. reclaim): receipt, then rethrow.
            withContext(NonCancellable) {
                writeReceipt(purpose, host, operation, written, EgressOutcome.FAILED)
            }
            throw cancelled
        } catch (failure: Exception) {
            writeReceipt(purpose, host, operation, written, EgressOutcome.FAILED)
            return Result.failure(failure)
        }
    }

    /** The enforcement matrix. Null = allowed; non-null = the denial reason. */
    private suspend fun denialFor(
        purpose: EgressPurpose,
        userInitiated: Boolean,
        host: String,
    ): String? =
        when (purpose) {
            EgressPurpose.ZOO_DOWNLOAD -> denialForZoo(userInitiated)
            EgressPurpose.OFF_LOOKUP -> denialForOffLookup()
            else -> denialForConsented(purpose, host)
        }

    private fun denialForZoo(userInitiated: Boolean): String? =
        if (userInitiated) {
            null
        } else {
            "zoo downloads require the user-initiated ModelManager flow (R-S14)"
        }

    private suspend fun denialForOffLookup(): String? =
        if (offLookupPolicy.isFoodDbLookupAllowed()) {
            null
        } else {
            "R-C4 food-database integration toggle is off"
        }

    /** FUTURE_* purposes: fail closed without an active capability grant. */
    private suspend fun denialForConsented(
        purpose: EgressPurpose,
        host: String,
    ): String? {
        // Unreachable by construction (every FUTURE_* maps a category) — but a
        // missing mapping fails CLOSED rather than opening a socket.
        val capability = purpose.requiresCapability ?: return "purpose $purpose has no enforcement path — refusing"
        if (!consentGate.isGranted(capability)) return "no active ${capability.wireName} grant"
        return if (host.isBlank()) "blank host" else null
    }

    private suspend fun writeReceipt(
        purpose: EgressPurpose,
        host: String,
        operation: String,
        bytes: Long,
        outcome: EgressOutcome,
    ) {
        ledger.append(
            PendingReceipt(
                purpose = purpose,
                host = host,
                operation = operation,
                bytes = bytes,
                outcome = outcome,
                atEpochMs = timeSource.nowEpochMs(),
            ),
        )
    }

    override fun close() {
        if (httpClient == null) client.close()
    }

    public companion object {
        /**
         * ADR-004: the whole-call request timeout is DISABLED (null) so future
         * SSE/BYOK streams are never killed mid-flight; [SOCKET_INACTIVITY_MS]
         * is the inactivity guard.
         */
        public const val SOCKET_INACTIVITY_MS: Long = 30_000L

        public const val CONNECT_TIMEOUT_MS: Long = 15_000L

        public const val DEFAULT_CACHE_ENTRIES: Int = 128

        internal const val DOWNLOAD_CHUNK_BYTES: Int = 64 * 1024

        /** Host extraction for receipting + malformed/blank-host refusal. */
        public fun hostOf(url: String): String? {
            val host = runCatching { URI(url).host }.getOrNull() ?: return null
            return host.lowercase().takeIf { it.isNotBlank() }
        }
    }
}

/**
 * Single-client HTTP stack: OkHttp engine (HTTP/2 per ADR-004 research;
 * the Ktor Android engine lacks it) + the ADR-004 timeout posture.
 */
public fun defaultEgressClient(): HttpClient =
    HttpClient(OkHttp) {
        expectSuccess = false // status is checked and receipted explicitly
        install(ContentNegotiation) {
            json(houseJson)
        }
        install(HttpTimeout) {
            // ADR-004: null whole-call timeout (SSE-safe) + inactivity guard.
            requestTimeoutMillis = null
            socketTimeoutMillis = NetworkDispatcher.SOCKET_INACTIVITY_MS
            connectTimeoutMillis = NetworkDispatcher.CONNECT_TIMEOUT_MS
        }
    }

/** Bounded access-ordered LRU behind [NetworkDispatcher]'s cache-first store. */
private class ResponseLruCache(
    private val maxEntries: Int,
) : LinkedHashMap<String, Any>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean = size > maxEntries
}

/**
 * ADR-004 house JSON: explicit `schemaVersion` envelopes, defaults for new
 * fields, unknown keys tolerated (forward-compat). Used by ContentNegotiation
 * and the future in-module OFF/BYOK clients.
 */
public val houseJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "kind"
    }
