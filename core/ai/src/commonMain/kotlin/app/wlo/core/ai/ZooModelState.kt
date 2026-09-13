package app.wlo.core.ai

/**
 * Per-model zoo state (R-S14): NOT_DOWNLOADED / DOWNLOADING(progress) /
 * DOWNLOADED_VERIFIED / CORRUPTED. States are observed (Flow), never polled;
 * CORRUPTED heals through a re-download ([ZooManager.ensureAvailable] retries
 * anything not yet [DownloadedVerified] — download-once applies to VERIFIED
 * files only).
 */
public sealed class ZooModelState {
    public data object NotDownloaded : ZooModelState()

    public data class Downloading(
        public val bytesSoFar: Long,
        public val totalBytes: Long,
    ) : ZooModelState()

    public data class DownloadedVerified(
        public val sizeBytes: Long,
        public val sha256Hex: String,
        /** Epoch ms of the install (file mtime; 0 when unknown). */
        public val installedAtEpochMs: Long,
    ) : ZooModelState()

    /** Hash (or size) mismatch: the artifact is quarantined, never installed. */
    public data class Corrupted(
        public val expectedSha256Hex: String,
        public val actualSha256Hex: String?,
    ) : ZooModelState()
}
