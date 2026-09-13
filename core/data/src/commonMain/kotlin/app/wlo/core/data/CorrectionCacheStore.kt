package app.wlo.core.data

import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.documents.CorrectionCacheDocument
import app.wlo.core.documents.CorrectionCacheEntry
import app.wlo.core.documents.DocumentCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The R-B6 correction cache's store (F02 §3 "corrections persist as ground
 * truth and visibly improve future estimates"; F09 will share this — one
 * mechanism, R-B6). In-process mirror FIRST (the capture loop runs at frame
 * rate and reads on every scan), DataStore document as the durable layer:
 * reads never block on I/O, writes are write-through.
 *
 * OWNER FLAG (M4): document-backed by design — a schema-v6 table was
 * deliberately avoided this milestone (see CorrectionCacheDocument). The
 * store API is intentionally table-shaped so the swap is mechanical.
 */
public class CorrectionCacheStore(
    private val documents: JsonDocumentStore,
) {
    private val mirror = MutableStateFlow(CorrectionCacheDocument())
    private val state: StateFlow<CorrectionCacheDocument> = mirror.asStateFlow()

    /** The current cache (in-memory; loaded lazily on first [ensureLoaded]). */
    public val current: CorrectionCacheDocument
        get() = mirror.value

    /**
     * Loads the persisted document once (the composition root calls this at
     * startup; corrupt payloads decode to the empty document, never a crash).
     */
    public suspend fun ensureLoaded(): CorrectionCacheDocument {
        if (mirror.value.revision == LOADED_MARK) return mirror.value
        val text = documents.readText(KEY)
        val document =
            text
                ?.let { raw -> runCatching { DocumentCodec.json.decodeFromString(CorrectionCacheDocument.serializer(), raw) }.getOrNull() }
                ?: CorrectionCacheDocument()
        mirror.value = document.copy(revision = LOADED_MARK)
        return mirror.value
    }

    /** The prior for one analyzed label, or null. */
    public fun priorFor(analyzedLabel: String): CorrectionCacheEntry? =
        mirror.value.entries.firstOrNull { it.analyzedLabel == analyzedLabel }

    /**
     * Records one accepted correction (label swap and/or portion edit).
     * One prior per analyzed label — the latest correction wins the target,
     * the count accumulates (the prior's weight), the portion is sticky.
     */
    public suspend fun recordCorrection(
        analyzedLabel: String,
        correctedLabel: String,
        correctedFoodId: String? = null,
        lastGrams: Double? = null,
        atEpochMs: Long,
    ) {
        val existing = mirror.value.entries
        val prior = existing.firstOrNull { it.analyzedLabel == analyzedLabel }
        val updated =
            (
                existing.filterNot { it.analyzedLabel == analyzedLabel } +
                    CorrectionCacheEntry(
                        analyzedLabel = analyzedLabel,
                        correctedLabel = correctedLabel,
                        correctedFoodId = correctedFoodId ?: prior?.correctedFoodId,
                        occurrences = (prior?.occurrences ?: 0) + 1,
                        lastGrams = lastGrams ?: prior?.lastGrams,
                        updatedAtEpochMs = atEpochMs,
                    )
            ).sortedByDescending { it.updatedAtEpochMs }
                .take(MAX_ENTRIES)
        persist(CorrectionCacheDocument(schemaVersion = CorrectionCacheDocument.CURRENT_SCHEMA_VERSION, entries = updated))
    }

    /** Wipes the cache (Fresh Start ledger companion; reversible is the diary's job, not ours). */
    public suspend fun clear() {
        persist(CorrectionCacheDocument())
    }

    private suspend fun persist(document: CorrectionCacheDocument) {
        mirror.value = document
        documents.writeText(KEY, DocumentCodec.json.encodeToString(CorrectionCacheDocument.serializer(), document))
    }

    public companion object {
        private const val KEY: String = "correction-cache"

        /** Revision marker meaning "loaded from disk at least once". */
        private const val LOADED_MARK: String = "loaded"

        /** A prior, not an archive — bounded (LRU by recency). */
        public const val MAX_ENTRIES: Int = 128
    }
}
