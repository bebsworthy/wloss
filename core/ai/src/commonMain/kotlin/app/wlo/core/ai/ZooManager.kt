package app.wlo.core.ai

import app.wlo.core.ports.EgressDownload
import app.wlo.core.ports.EgressPort
import app.wlo.core.ports.ModelHandle
import app.wlo.core.ports.ModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.Sink
import okio.Timeout
import okio.buffer

/**
 * The rich zoo surface (states, catalog, storage accounting) on top of the
 * [ModelManager] port. `:app` binds [OkioZooManager] as BOTH; the debug egress
 * monitor and PART B's zoo UI consume this interface.
 */
public interface ZooManager : ModelManager {
    /** Manifest entries for size disclosure + model cards (no network). */
    public fun catalog(): List<ZooModel>

    public fun state(modelId: String): ZooModelState

    public fun observeStates(): Flow<Map<String, ZooModelState>>

    /** Total zoo bytes on disk (storage accounting, one-tap-reclaim screens). */
    public suspend fun storageBytes(): Long
}

/**
 * The R-S14 zoo manager: download-once, hash-verify, atomic-install, storage
 * accounting, one-tap reclaim. Every download rides the [EgressPort] choke
 * point (user-initiated, size-disclosed — the dispatcher receipts it); the
 * sha256 is computed by a [HashingSink] in the same pass the bytes stream, so
 * verification costs zero extra reads. Install is atomic (`.part` temp file →
 * [FileSystem.atomicMove]); a hash/size mismatch quarantines the artifact
 * ([ZooModelState.Corrupted]) and the temp file is deleted — nothing
 * unverified is ever installed. Transport failures are NOT corruption: they
 * return the model to [ZooModelState.NotDownloaded] for a later retry.
 * Airplane-mode parity: once [ZooModelState.DownloadedVerified],
 * [ensureAvailable] never touches the network again.
 *
 * Storage is plain okio over app-private paths (injected [FileSystem] +
 * [baseDir]), which keeps this class JVM-testable and Android-identical.
 */
public class OkioZooManager(
    private val manifest: ZooManifest,
    private val egress: EgressPort,
    private val fileSystem: FileSystem,
    private val baseDir: Path,
) : ZooManager {
    private val states = MutableStateFlow<Map<String, ZooModelState>>(emptyMap())
    private val lockRegistryMutex = Mutex()
    private val modelLocks = mutableMapOf<String, Mutex>()

    init {
        states.value = manifest.models.associate { model -> model.id to initialState(model) }
    }

    /** Size-disclosure API: what the UI shows BEFORE any download (R-S14). */
    override fun catalog(): List<ZooModel> = manifest.models

    override fun state(modelId: String): ZooModelState = states.value[modelId] ?: ZooModelState.NotDownloaded

    override fun observeStates(): Flow<Map<String, ZooModelState>> = states.asStateFlow()

    /** Bytes on disk across the whole zoo (model files + leftover temp files). */
    override suspend fun storageBytes(): Long =
        fileSystem.listOrNull(baseDir)?.sumOf { path ->
            fileSystem.metadataOrNull(path)?.size ?: 0L
        } ?: 0L

    override suspend fun ensureAvailable(modelId: String): Result<ModelHandle> {
        val entry =
            manifest.byId(modelId)
                ?: return Result.failure(IllegalArgumentException("unknown zoo model: $modelId"))

        return lockFor(modelId).withLock {
            when (val current = state(modelId)) {
                // Download-once: a VERIFIED artifact never re-downloads — the
                // receipt ledger proves the single network trip (R-S14).
                is ZooModelState.DownloadedVerified ->
                    Result.success(handleFor(entry, current))

                is ZooModelState.Downloading ->
                    Result.failure(IllegalStateException("download already in progress: $modelId"))

                // NotDownloaded and Corrupted both (re-)download — re-download
                // is how CORRUPTED heals.
                ZooModelState.NotDownloaded, is ZooModelState.Corrupted ->
                    downloadAndInstall(entry)
            }
        }
    }

    /** One-tap reclaim (R-S14): delete + state reset. Append-only receipts stay. */
    override suspend fun reclaim(modelId: String): Result<Unit> {
        val entry =
            manifest.byId(modelId)
                ?: return Result.failure(IllegalArgumentException("unknown zoo model: $modelId"))
        return lockFor(modelId).withLock {
            fileSystem.delete(installedPath(entry), mustExist = false)
            fileSystem.delete(tempPath(modelId), mustExist = false)
            setState(modelId, ZooModelState.NotDownloaded)
            Result.success(Unit)
        }
    }

    private suspend fun downloadAndInstall(entry: ZooModel): Result<ModelHandle> {
        val temp = tempPath(entry.id)
        fileSystem.createDirectories(baseDir)
        setState(entry.id, ZooModelState.Downloading(bytesSoFar = 0, totalBytes = entry.sizeBytes))

        // HashingSink decorates the file sink: it hashes AND forwards every
        // byte in the same pass; closing it closes the file beneath.
        val hashing = HashingSink.sha256(fileSystem.sink(temp))
        val result =
            hashing.use {
                egress.download(
                    request =
                        EgressDownload(
                            modelId = entry.id,
                            url = entry.url,
                            expectedBytes = entry.sizeBytes,
                            userInitiated = true, // the ModelManager flow, post-disclosure
                            onProgress = { soFar ->
                                setState(
                                    entry.id,
                                    ZooModelState.Downloading(bytesSoFar = soFar, totalBytes = entry.sizeBytes),
                                )
                            },
                        ),
                    sink = it,
                )
            }

        val written = result.getOrNull()
        if (written == null) {
            // Transport failure (offline, HTTP error, oversized cut): remove the
            // temp file; this is retry-later, not corruption.
            fileSystem.delete(temp, mustExist = false)
            setState(entry.id, ZooModelState.NotDownloaded)
            return Result.failure(result.exceptionOrNull() ?: IllegalStateException("zoo download failed"))
        }

        val actualHex = hashing.hash.hex()
        if (written != entry.sizeBytes || !actualHex.equals(entry.sha256Hex, ignoreCase = true)) {
            // Verify BEFORE install: quarantine, delete the temp, say so.
            fileSystem.delete(temp, mustExist = false)
            setState(
                entry.id,
                ZooModelState.Corrupted(expectedSha256Hex = entry.sha256Hex, actualSha256Hex = actualHex),
            )
            return Result.failure(
                IllegalStateException(
                    "zoo artifact failed verification: ${entry.id} " +
                        "(bytes $written/${entry.sizeBytes}, sha256 $actualHex)",
                ),
            )
        }

        val installed = installedPath(entry)
        fileSystem.atomicMove(temp, installed)
        val verified =
            ZooModelState.DownloadedVerified(
                sizeBytes = written,
                sha256Hex = actualHex.lowercase(),
                installedAtEpochMs = fileSystem.metadataOrNull(installed)?.lastModifiedAtMillis ?: 0L,
            )
        setState(entry.id, verified)
        return Result.success(handleFor(entry, verified))
    }

    /** Disk truth at startup: verify whatever is present, surface CORRUPTED. */
    private fun initialState(model: ZooModel): ZooModelState {
        val installed = installedPath(model)
        val metadata = fileSystem.metadataOrNull(installed) ?: return ZooModelState.NotDownloaded
        val onDiskBytes = metadata.size ?: return ZooModelState.NotDownloaded
        val actual = hashOf(installed) ?: return ZooModelState.NotDownloaded
        return if (onDiskBytes == model.sizeBytes && actual.equals(model.sha256Hex, ignoreCase = true)) {
            ZooModelState.DownloadedVerified(
                sizeBytes = onDiskBytes,
                sha256Hex = actual.lowercase(),
                installedAtEpochMs = metadata.lastModifiedAtMillis ?: 0L,
            )
        } else {
            ZooModelState.Corrupted(expectedSha256Hex = model.sha256Hex, actualSha256Hex = actual)
        }
    }

    private fun hashOf(path: Path): String? =
        runCatching {
            fileSystem.source(path).use { source ->
                val hashing = HashingSink.sha256(DISCARD)
                val scratch = Buffer()
                source.buffer().use { buffered ->
                    while (buffered.read(scratch, READ_CHUNK_BYTES) != -1L) {
                        hashing.write(scratch, scratch.size)
                        scratch.clear()
                    }
                }
                hashing.hash.hex()
            }
        }.getOrNull()

    private fun setState(
        modelId: String,
        state: ZooModelState,
    ) {
        states.update { current -> current + (modelId to state) }
    }

    private suspend fun lockFor(modelId: String): Mutex = lockRegistryMutex.withLock { modelLocks.getOrPut(modelId) { Mutex() } }

    private fun tempPath(modelId: String): Path = baseDir / "${sanitize(modelId)}.part"

    private fun installedPath(entry: ZooModel): Path = baseDir / "${sanitize(entry.id)}.${entry.format}"

    /** Verified state → the analyzer-facing handle, carrying the artifact path. */
    private fun handleFor(
        entry: ZooModel,
        verified: ZooModelState.DownloadedVerified,
    ): ModelHandle =
        ModelHandle(
            modelId = entry.id,
            sizeBytes = verified.sizeBytes,
            sha256Hex = verified.sha256Hex,
            filePath = installedPath(entry).toString(),
        )

    public companion object {
        internal const val READ_CHUNK_BYTES: Long = 64L * 1024

        /** A write-nowhere sink for hash-only passes. */
        private val DISCARD: Sink =
            object : Sink {
                override fun write(
                    source: Buffer,
                    byteCount: Long,
                ) {
                    source.skip(byteCount)
                }

                override fun flush() {}

                override fun close() {}

                override fun timeout(): Timeout = Timeout.NONE
            }

        /** Model ids contain `/` (family/version) — paths must not. */
        internal fun sanitize(modelId: String): String = modelId.replace('/', '_')
    }
}
