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

/** A verified, installed zoo model. */
public data class ModelHandle(
    public val modelId: String,
    public val sizeBytes: Long,
    public val sha256Hex: String,
)
