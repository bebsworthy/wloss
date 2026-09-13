package app.wlo.core.ports

/**
 * Model-zoo manager (F12 §3.2, R-S14): download-on-first-use only — the APK
 * ships no zoo models. Handles hash-pinned URLs, verification, storage
 * accounting and one-tap reclaim.
 */
public interface ModelManager {
    public suspend fun ensureAvailable(modelId: String): Result<ModelHandle>

    public suspend fun reclaim(modelId: String): Result<Unit>
}

/**
 * A verified, installed zoo model. [filePath] is the absolute path of the
 * verified artifact (the analyzer loads it with `OrtSession` from an arbitrary
 * path); null only for handles built without storage knowledge (tests, fakes)
 * — real zoo handles always carry it.
 */
public data class ModelHandle(
    public val modelId: String,
    public val sizeBytes: Long,
    public val sha256Hex: String,
    public val filePath: String? = null,
)
